package iuh.fit.service.file;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import iuh.fit.dto.request.file.FileUploadRequest;
import iuh.fit.dto.response.file.FileUploadResponse;
import iuh.fit.entity.FileUpload;
import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;
import iuh.fit.repository.FileUploadRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Cloudinary Service Implementation
 * Triển khai upload/delete file lên Cloudinary
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CloudinaryService implements FileStorageService {

    Cloudinary cloudinary;
    FileUploadRepository fileUploadRepository;

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, FileUploadRequest request, String userId) {
        try {
            // Validate file
            validateFile(file);

            // Xác định loại file
            FileType fileType = determineFileType(file, request.getFileType());

            // Upload lên Cloudinary
            Map<String, Object> uploadParams = buildUploadParams(request, fileType);
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);

            // Tạo entity và lưu vào database
            FileUpload fileUpload = buildFileUploadEntity(file, uploadResult, request, fileType, userId);
            fileUpload = fileUploadRepository.save(fileUpload);

            log.info("File uploaded successfully: {} by user: {}", fileUpload.getFileId(), userId);
            return mapToResponse(fileUpload);

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Lỗi khi upload file: " + e.getMessage());
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
        List<FileUpload> files = fileUploadRepository.findByUploadedByAndStatus(userId, "ACTIVE");
        return files.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteFile(String fileId, String userId) {
        try {
            FileUpload fileUpload = fileUploadRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy file với ID: " + fileId));

            // Kiểm tra quyền xóa
            if (!fileUpload.getUploadedBy().equals(userId)) {
                throw new RuntimeException("Bạn không có quyền xóa file này");
            }

            // Xóa file trên Cloudinary
            String resourceType = getResourceType(fileUpload.getFileType());
            cloudinary.uploader().destroy(fileUpload.getResourceId(),
                    ObjectUtils.asMap("resource_type", resourceType));

            // Đánh dấu file là DELETED
            fileUpload.setStatus("DELETED");
            fileUpload.setDeletedAt(LocalDateTime.now());
            fileUploadRepository.save(fileUpload);

            log.info("File deleted successfully: {} by user: {}", fileId, userId);
            return true;

        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage());
            throw new RuntimeException("Lỗi khi xóa file: " + e.getMessage());
        }
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
        // Cloudinary URLs are public by default
        // For private URLs, you would need to use authenticated URLs
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy file với ID: " + fileId));
        return fileUpload.getFileUrl();
    }

    // ===== HELPER METHODS =====

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File không được để trống");
        }

        // Validate file size (max 10MB mặc định)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new RuntimeException("File quá lớn. Kích thước tối đa là 10MB");
        }
    }

    private FileType determineFileType(MultipartFile file, FileType requestedType) {
        if (requestedType != null) {
            return requestedType;
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return FileType.OTHER;
        }

        if (contentType.startsWith("image/")) return FileType.IMAGE;
        if (contentType.startsWith("video/")) return FileType.VIDEO;
        if (contentType.startsWith("audio/")) return FileType.AUDIO;
        if (contentType.startsWith("application/pdf") || contentType.contains("document"))
            return FileType.DOCUMENT;
        if (contentType.contains("zip") || contentType.contains("rar"))
            return FileType.ARCHIVE;

        return FileType.OTHER;
    }

    private Map<String, Object> buildUploadParams(FileUploadRequest request, FileType fileType) {
        Map<String, Object> params = new HashMap<>();

        // Folder path
        String folder = request.getFolderPath() != null ? request.getFolderPath() : "general";
        params.put("folder", "fruvia/" + folder);

        // Resource type
        params.put("resource_type", getResourceType(fileType));

        // Optimization
        if (Boolean.TRUE.equals(request.getOptimize()) && fileType == FileType.IMAGE) {
            params.put("quality", "auto");
            params.put("fetch_format", "auto");
        }

        // Generate thumbnail for video
        if (Boolean.TRUE.equals(request.getGenerateThumbnail()) && fileType == FileType.VIDEO) {
            params.put("eager", Arrays.asList(
                    ObjectUtils.asMap("width", 300, "height", 300, "crop", "pad", "format", "jpg")
            ));
        }

        return params;
    }

    private String getResourceType(FileType fileType) {
        return switch (fileType) {
            case IMAGE -> "image";
            case VIDEO -> "video";
            case AUDIO -> "video"; // Cloudinary uses 'video' for audio files
            default -> "raw";
        };
    }

    private FileUpload buildFileUploadEntity(MultipartFile file, Map uploadResult,
                                             FileUploadRequest request, FileType fileType, String userId) {
        String fileUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");
        String thumbnailUrl = null;

        // Get thumbnail URL for video
        if (fileType == FileType.VIDEO && uploadResult.containsKey("eager")) {
            List<Map> eager = (List<Map>) uploadResult.get("eager");
            if (!eager.isEmpty()) {
                thumbnailUrl = (String) eager.get(0).get("secure_url");
            }
        }

        return FileUpload.builder()
                .fileId(UUID.randomUUID().toString())
                .originalFileName(file.getOriginalFilename())
                .storedFileName(publicId)
                .fileType(fileType)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .fileUrl(fileUrl)
                .thumbnailUrl(thumbnailUrl)
                .storageProvider(StorageProvider.CLOUDINARY)
                .resourceId(publicId)
                .folderPath(request.getFolderPath())
                .uploadedBy(userId)
                .uploadedAt(LocalDateTime.now())
                .description(request.getDescription())
                .tags(request.getTags())
                .status("ACTIVE")
                .build();
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
