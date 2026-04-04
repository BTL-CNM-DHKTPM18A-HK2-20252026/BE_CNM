package iuh.fit.service.image;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import iuh.fit.dto.request.image.ImageUploadCompleteRequest;
import iuh.fit.dto.response.image.ImagePresignedUrlResponse;
import iuh.fit.dto.response.image.ImageUploadMetadataResponse;
import iuh.fit.entity.ImageUploadMetadata;
import iuh.fit.repository.ImageUploadMetadataRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ImageUploadService {

    static long PRESIGNED_EXPIRE_SECONDS = 300L;

    S3Client s3Client;
    S3Presigner s3Presigner;
    ImageUploadMetadataRepository imageUploadMetadataRepository;

        @NonFinal
    @Value("${aws.s3.bucket-name}")
    String bucketName;

    public ImagePresignedUrlResponse generatePutPresignedUrl(String fileName, String contentType) {
        String safeOriginalName = sanitizeFileName(fileName);
        String uniqueFileName = UUID.randomUUID() + "_" + safeOriginalName;
        String s3Key = "images/" + uniqueFileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_EXPIRE_SECONDS))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(putObjectPresignRequest);
        String s3Url = s3Client.utilities()
                .getUrl(builder -> builder.bucket(bucketName).key(s3Key))
                .toExternalForm();

        return ImagePresignedUrlResponse.builder()
                .originalName(fileName)
                .s3Key(s3Key)
                .s3Url(s3Url)
                .presignedUrl(presignedRequest.url().toString())
                .contentType(contentType)
                .expiresInSeconds(PRESIGNED_EXPIRE_SECONDS)
                .build();
    }

    public ImageUploadMetadataResponse saveUploadedImageMetadata(ImageUploadCompleteRequest request) {
        String resolvedUrl = StringUtils.hasText(request.getS3Url())
                ? request.getS3Url()
                : s3Client.utilities().getUrl(builder -> builder.bucket(bucketName).key(request.getS3Key()))
                        .toExternalForm();

        ImageUploadMetadata metadata = ImageUploadMetadata.builder()
                .originalName(request.getOriginalName())
                .s3Key(request.getS3Key())
                .s3Url(resolvedUrl)
                .uploadTime(LocalDateTime.now())
                .build();

        ImageUploadMetadata saved = imageUploadMetadataRepository.save(metadata);

        return ImageUploadMetadataResponse.builder()
                .id(saved.getId())
                .originalName(saved.getOriginalName())
                .s3Key(saved.getS3Key())
                .s3Url(saved.getS3Url())
                .uploadTime(saved.getUploadTime())
                .build();
    }

    private String sanitizeFileName(String fileName) {
        String baseName = fileName;
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex < fileName.length() - 1) {
            baseName = fileName.substring(slashIndex + 1);
        }

        String sanitized = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return StringUtils.hasText(sanitized) ? sanitized : "image";
    }
}
