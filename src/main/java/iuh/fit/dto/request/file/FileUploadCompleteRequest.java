package iuh.fit.dto.request.file;

import iuh.fit.enums.FileType;

/**
 * Request DTO để lưu metadata sau khi upload trực tiếp lên S3
 */
public class FileUploadCompleteRequest {
    private String originalFileName; 
    private String storedFileName;
    private String resourceId;       
    private String fileUrl;          
    private Long fileSize;
    private FileType fileType;
    private String mimeType;
    private String folderPath;
    private String description;
    
    // For images specifically
    private Integer width;
    private Integer height;

    public FileUploadCompleteRequest() {}

    public FileUploadCompleteRequest(String originalFileName, String storedFileName, String resourceId, String fileUrl, 
                                   Long fileSize, FileType fileType, String mimeType, String folderPath, 
                                   String description, Integer width, Integer height) {
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.resourceId = resourceId;
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.folderPath = folderPath;
        this.description = description;
        this.width = width;
        this.height = height;
    }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    // Aliases for compatibility with old frontend code
    public String getOriginalName() { return originalFileName; }
    public void setOriginalName(String name) { this.originalFileName = name; }
    
    public String getS3Key() { return resourceId; }
    public void setS3Key(String key) { this.resourceId = key; }
    
    public String getS3Url() { return fileUrl; }
    public void setS3Url(String url) { this.fileUrl = url; }
}
