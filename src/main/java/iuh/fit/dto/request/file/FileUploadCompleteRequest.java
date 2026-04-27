package iuh.fit.dto.request.file;

import iuh.fit.enums.FileType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO để lưu metadata sau khi upload trực tiếp lên S3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUploadCompleteRequest {
    String originalFileName; // hoặc originalName
    String storedFileName;
    String resourceId;       // hoặc s3Key
    String fileUrl;          // hoặc s3Url
    Long fileSize;
    FileType fileType;
    String mimeType;
    String folderPath;
    String description;
    
    // For images specifically
    Integer width;
    Integer height;

    // Aliases for compatibility with old frontend code
    public String getOriginalName() { return originalFileName; }
    public void setOriginalName(String name) { this.originalFileName = name; }
    
    public String getS3Key() { return resourceId; }
    public void setS3Key(String key) { this.resourceId = key; }
    
    public String getS3Url() { return fileUrl; }
    public void setS3Url(String url) { this.fileUrl = url; }
}
