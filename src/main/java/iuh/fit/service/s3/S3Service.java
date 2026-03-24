package iuh.fit.service.s3;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

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

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, objectKey)
                        .withMethod(HttpMethod.PUT)
                        .withExpiration(expiration);
        
        // Ensure the content-type is expected by S3 (optional but recommended for front-end PUT)
        // generatePresignedUrlRequest.addRequestParameter("Content-Type", fileType);

        return s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
    }
}
