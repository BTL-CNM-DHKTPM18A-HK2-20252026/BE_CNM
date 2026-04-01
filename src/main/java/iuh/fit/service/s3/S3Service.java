package iuh.fit.service.s3;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final AmazonS3 s3Client;

    @Value("${AWS_S3_BUCKET_NAME}")
    private String bucketName;

    /**
     * Generates a pre-signed URL for uploading a file to S3.
     * 
     * @param fileName The name of the file
     * @param fileType The category of the file (images, videos, files)
     * @return Pre-signed URL string
     */
    public String generatePresignedUrl(String fileName, String fileType) {
        String folder = "";
        if (fileType.startsWith("image")) {
            folder = "messages/images/";
        } else if (fileType.startsWith("video")) {
            folder = "messages/videos/";
        } else {
            folder = "messages/files/";
        }

        // Generate unique name to avoid collisions
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        String objectKey = folder + uniqueFileName;

        // Set expiration time (e.g., 10 minutes)
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * 10;
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration);

        return s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
    }

    /**
     * Generates a pre-signed URL for uploading a profile or cover photo to S3.
     * Files are stored under profiles/{userId}/ to avoid collision with message
     * assets.
     */
    public String generatePresignedUrlForProfile(String fileName, String fileType, String userId) {
        String folder = "profiles/" + userId + "/";
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        String objectKey = folder + uniqueFileName;

        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + 1000L * 60 * 10);

        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration);

        return s3Client.generatePresignedUrl(req).toString();
    }

    /**
     * Uploads bytes directly to S3 and keeps the object key stable.
     */
    public void uploadBytes(String objectKey, byte[] data, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(contentType);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            s3Client.putObject(bucketName, objectKey, inputStream, metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload generated image to S3", ex);
        }
    }

    /**
     * Generates a pre-signed GET URL for reading a private S3 object.
     */
    public String generatePresignedReadUrl(String objectKey, long expiryMinutes) {
        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + (Math.max(1, expiryMinutes) * 60_000L));

        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);

        return s3Client.generatePresignedUrl(req).toString();
    }
}
