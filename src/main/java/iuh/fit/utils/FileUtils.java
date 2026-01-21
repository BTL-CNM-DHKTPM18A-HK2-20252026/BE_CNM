package iuh.fit.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * File handling utility methods for Fruvia Chat
 */
public class FileUtils {

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain",
        "application/zip"
    );

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;  // 50MB

    /**
     * Validate if file is an image
     */
    public static boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String contentType = file.getContentType();
        return contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * Validate if file type is allowed
     */
    public static boolean isValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String contentType = file.getContentType();
        return contentType != null && 
               (ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase()) || 
                ALLOWED_FILE_TYPES.contains(contentType.toLowerCase()));
    }

    /**
     * Check file size for images
     */
    public static boolean isValidImageSize(MultipartFile file) {
        return file != null && file.getSize() <= MAX_IMAGE_SIZE;
    }

    /**
     * Check file size for general files
     */
    public static boolean isValidFileSize(MultipartFile file) {
        return file != null && file.getSize() <= MAX_FILE_SIZE;
    }

    /**
     * Get file extension from filename
     */
    public static String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Format file size to human-readable format
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Generate unique filename with timestamp
     */
    public static String generateUniqueFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return System.currentTimeMillis() + ".file";
        }

        String extension = getFileExtension(originalFilename);
        String nameWithoutExt = originalFilename.substring(0, originalFilename.lastIndexOf("."));
        
        // Sanitize filename
        nameWithoutExt = nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        return nameWithoutExt + "_" + System.currentTimeMillis() + "." + extension;
    }

    /**
     * Check if file exists
     */
    public static boolean fileExists(String filePath) {
        if (filePath == null) {
            return false;
        }
        return Files.exists(Path.of(filePath));
    }

    /**
     * Delete file from filesystem
     */
    public static boolean deleteFile(String filePath) {
        try {
            if (filePath != null) {
                return Files.deleteIfExists(Path.of(filePath));
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * Get MIME type from file
     */
    public static String getMimeType(Path filePath) {
        try {
            return Files.probeContentType(filePath);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
