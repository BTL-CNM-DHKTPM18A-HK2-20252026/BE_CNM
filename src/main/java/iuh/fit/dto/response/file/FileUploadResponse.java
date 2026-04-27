package iuh.fit.dto.response.file;

import java.time.LocalDateTime;

import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * File Upload Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUploadResponse {

    String fileId;
    String originalFileName;
    String storedFileName;
    FileType fileType;
    String mimeType;
    Long fileSize;
    String fileUrl;
    String thumbnailUrl;
    StorageProvider storageProvider;
    String resourceId;
    String folderPath;
    String uploadedBy;
    LocalDateTime uploadedAt;
    String description;
    String[] tags;
    String status;
    String presignedUrl;
}
