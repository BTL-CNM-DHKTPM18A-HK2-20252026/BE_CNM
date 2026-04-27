package iuh.fit.service.file;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import iuh.fit.dto.request.file.FileUploadRequest;
import iuh.fit.dto.request.file.FileUploadCompleteRequest;
import iuh.fit.dto.response.file.FileUploadResponse;
import iuh.fit.entity.FileUpload;
import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;
import iuh.fit.repository.FileUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * S3 Storage Service Implementation using AWS SDK v1
 * Triển khai upload/delete file lên AWS S3 (Tương tự pattern của S3Service)
 */
@Service
@Primary
public class S3StorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageServiceImpl.class);

    private final AmazonS3 s3Client;
    private final FileUploadRepository fileUploadRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public S3StorageServiceImpl(AmazonS3 s3Client, FileUploadRepository fileUploadRepository) {
        this.s3Client = s3Client;
        this.fileUploadRepository = fileUploadRepository;
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, FileUploadRequest request, String userId) {
        validateFile(file);

        String folder = request.getFolderPath() != null ? request.getFolderPath() : "general";
        String uniqueFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "fruvia/" + folder + "/" + uniqueFileName;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            s3Client.putObject(new PutObjectRequest(bucketName, key, file.getInputStream(), metadata));

            String fileUrl = s3Client.getUrl(bucketName, key).toString();

            FileUpload fileUpload = FileUpload.builder()
                    .fileId(UUID.randomUUID().toString())
                    .originalFileName(file.getOriginalFilename())
                    .storedFileName(uniqueFileName)
                    .fileType(determineFileType(file.getContentType()))
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .fileUrl(fileUrl)
                    .storageProvider(StorageProvider.AWS_S3)
                    .resourceId(key)
                    .folderPath(folder)
                    .uploadedBy(userId)
                    .uploadedAt(LocalDateTime.now())
                    .description(request.getDescription())
                    .tags(request.getTags())
                    .status("ACTIVE")
                    .build();

            fileUpload = fileUploadRepository.save(fileUpload);
            return mapToResponse(fileUpload);

        } catch (IOException e) {
            log.error("Failed to upload file to S3: {}", e.getMessage());
            throw new RuntimeException("Lỗi khi upload file lên S3: " + e.getMessage());
        }
    }

    @Override
    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, FileUploadRequest request, String userId) {
        return files.stream()
                .map(file -> uploadFile(file, request, userId))
                .collect(Collectors.toList());
    }

    @Override
    public FileUploadResponse getFileById(String fileId) {
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy file với ID: " + fileId));
        return mapToResponse(fileUpload);
    }

    @Override
    public List<FileUploadResponse> getFilesByUserId(String userId) {
        return fileUploadRepository.findByUploadedByOrderByUploadedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteFile(String fileId, String userId) {
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy file với ID: " + fileId));

        s3Client.deleteObject(bucketName, fileUpload.getResourceId());
        fileUploadRepository.delete(fileUpload);
        return true;
    }

    @Override
    public int deleteMultipleFiles(List<String> fileIds, String userId) {
        int deletedCount = 0;
        for (String fileId : fileIds) {
            try {
                if (deleteFile(fileId, userId)) {
                    deletedCount++;
                }
            } catch (Exception e) {
                log.error("Error deleting file {}: {}", fileId, e.getMessage());
            }
        }
        return deletedCount;
    }

    @Override
    public String getTemporaryUrl(String fileId, int duration) {
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy file với ID: " + fileId));
        
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000L * duration;
        expiration.setTime(expTimeMillis);

        return s3Client.generatePresignedUrl(bucketName, fileUpload.getResourceId(), expiration).toString();
    }

    @Override
    public FileUploadResponse generateUploadUrl(String fileName, String contentType, String folderPath) {
        String folder = folderPath != null ? folderPath : "general";
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "fruvia/" + folder + "/" + uniqueFileName;

        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000L * 60 * 10; // 10 minutes
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest = 
            new GeneratePresignedUrlRequest(bucketName, key)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration)
                .withContentType(contentType);

        String presignedUrl = s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
        String fileUrl = s3Client.getUrl(bucketName, key).toString();

        return FileUploadResponse.builder()
                .originalFileName(fileName)
                .storedFileName(uniqueFileName)
                .fileUrl(fileUrl)
                .presignedUrl(presignedUrl)
                .resourceId(key)
                .folderPath(folder)
                .mimeType(contentType)
                .storageProvider(StorageProvider.AWS_S3)
                .build();
    }

    @Override
    public FileUploadResponse saveFileMetadata(FileUploadCompleteRequest request, String userId) {
        FileUpload fileUpload = FileUpload.builder()
                .fileId(UUID.randomUUID().toString())
                .originalFileName(request.getOriginalFileName() != null ? request.getOriginalFileName() : request.getOriginalName())
                .storedFileName(request.getStoredFileName())
                .fileType(request.getFileType() != null ? request.getFileType() : FileType.OTHER)
                .mimeType(request.getMimeType())
                .fileSize(request.getFileSize())
                .fileUrl(request.getFileUrl() != null ? request.getFileUrl() : request.getS3Url())
                .storageProvider(StorageProvider.AWS_S3)
                .resourceId(request.getResourceId() != null ? request.getResourceId() : request.getS3Key())
                .folderPath(request.getFolderPath())
                .uploadedBy(userId)
                .uploadedAt(LocalDateTime.now())
                .description(request.getDescription())
                .thumbnailUrl(request.getWidth() != null ? request.getWidth() + "x" + request.getHeight() : null) 
                .status("ACTIVE")
                .build();

        fileUpload = fileUploadRepository.save(fileUpload);
        return mapToResponse(fileUpload);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File không được để trống");
        }
    }

    private FileType determineFileType(String contentType) {
        if (contentType == null) return FileType.OTHER;
        if (contentType.startsWith("image/")) return FileType.IMAGE;
        if (contentType.startsWith("video/")) return FileType.VIDEO;
        if (contentType.startsWith("audio/")) return FileType.AUDIO;
        return FileType.DOCUMENT;
    }

    private FileUploadResponse mapToResponse(FileUpload fileUpload) {
        return FileUploadResponse.builder()
                .fileId(fileUpload.getFileId())
                .originalFileName(fileUpload.getOriginalFileName())
                .storedFileName(fileUpload.getStoredFileName())
                .fileType(fileUpload.getFileType())
                .mimeType(fileUpload.getMimeType())
                .fileSize(fileUpload.getFileSize())
                .fileUrl(fileUpload.getFileUrl())
                .thumbnailUrl(fileUpload.getThumbnailUrl())
                .storageProvider(fileUpload.getStorageProvider())
                .resourceId(fileUpload.getResourceId())
                .folderPath(fileUpload.getFolderPath())
                .uploadedBy(fileUpload.getUploadedBy())
                .uploadedAt(fileUpload.getUploadedAt())
                .description(fileUpload.getDescription())
                .tags(fileUpload.getTags())
                .status(fileUpload.getStatus())
                .build();
    }
}
