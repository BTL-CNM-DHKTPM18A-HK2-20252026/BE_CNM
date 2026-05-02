package iuh.fit.service.s3;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
     * Downloads object bytes directly from S3 using SDK credentials.
     * Use this for server-side access to private objects — avoids presigned URL
     * generation issues (malformed credentials, region mismatch, etc.).
     */
    public byte[] downloadBytes(String objectKey) {
        try (com.amazonaws.services.s3.model.S3Object s3Object = s3Client.getObject(bucketName, objectKey)) {
            return s3Object.getObjectContent().readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download S3 object: " + objectKey, ex);
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

    /**
     * Extracts the S3 object key from a full S3 URL.
     * e.g. https://bucket.s3.amazonaws.com/messages/images/uuid_file.png →
     * messages/images/uuid_file.png
     */
    private String extractKey(String url) {
        if (url == null)
            return null;
        try {
            int idx = url.indexOf(".com/");
            if (idx == -1)
                return null;
            String key = url.substring(idx + 5);
            // Decode the key because the URL has it encoded (e.g. %20 -> space)
            return java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decode S3 key from URL: {}", url);
            return null;
        }
    }

    /**
     * Deletes a single S3 object identified by its public URL.
     * Silently skips if the URL is not a recognised S3 URL.
     */
    public void deleteObject(String url) {
        String key = extractKey(url);
        if (key == null || key.isBlank())
            return;
        try {
            s3Client.deleteObject(bucketName, key);
            log.debug("Deleted S3 object: {}", key);
        } catch (Exception e) {
            log.warn("Could not delete S3 object [{}]: {}", key, e.getMessage());
        }
    }

    /**
     * Generates a pre-signed download URL that forces the browser to download the
     * file.
     * 
     * @param url      The public S3 URL of the file
     * @param fileName The name to be used for the downloaded file
     * @return Pre-signed URL string with attachment disposition
     */
    public String generateDownloadUrl(String url, String fileName) {
        String key = extractKey(url);
        if (key == null || key.isBlank())
            return url;

        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + 1000L * 60 * 15); // 15 minutes

        com.amazonaws.services.s3.model.ResponseHeaderOverrides overrides = new com.amazonaws.services.s3.model.ResponseHeaderOverrides()
                .withContentDisposition("attachment; filename=\"" + fileName + "\"");

        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration)
                .withResponseHeaders(overrides);

        return s3Client.generatePresignedUrl(req).toString();
    }

    /**
     * Deletes multiple S3 objects in parallel using a fixed thread pool.
     */
    public void deleteObjectsParallel(List<String> urls) {
        if (urls == null || urls.isEmpty())
            return;

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(urls.size(), 10));
        try {
            List<CompletableFuture<Void>> futures = urls.stream()
                    .filter(url -> url != null && url.contains(".amazonaws.com/"))
                    .map(url -> CompletableFuture.runAsync(() -> deleteObject(url), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("S3 parallel delete completed for {} objects", futures.size());
        } finally {
            executor.shutdown();
        }
    }
}
