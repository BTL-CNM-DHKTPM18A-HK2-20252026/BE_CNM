package iuh.fit.dto.response.file;

import java.time.LocalDateTime;
import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;

/**
 * File Upload Response DTO
 */
public class FileUploadResponse {

    private String fileId;
    private String originalFileName;
    private String storedFileName;
    private FileType fileType;
    private String mimeType;
    private Long fileSize;
    private String fileUrl;
    private String thumbnailUrl;
    private StorageProvider storageProvider;
    private String resourceId;
    private String folderPath;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private String description;
    private String[] tags;
    private String status;
    private String presignedUrl;

    public FileUploadResponse() {}

    public FileUploadResponse(String fileId, String originalFileName, String storedFileName, FileType fileType, 
                            String mimeType, Long fileSize, String fileUrl, String thumbnailUrl, 
                            StorageProvider storageProvider, String resourceId, String folderPath, 
                            String uploadedBy, LocalDateTime uploadedAt, String description, String[] tags, 
                            String status, String presignedUrl) {
        this.fileId = fileId;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.storageProvider = storageProvider;
        this.resourceId = resourceId;
        this.folderPath = folderPath;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.description = description;
        this.tags = tags;
        this.status = status;
        this.presignedUrl = presignedUrl;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public StorageProvider getStorageProvider() { return storageProvider; }
    public void setStorageProvider(StorageProvider storageProvider) { this.storageProvider = storageProvider; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPresignedUrl() { return presignedUrl; }
    public void setPresignedUrl(String presignedUrl) { this.presignedUrl = presignedUrl; }

    public static FileUploadResponseBuilder builder() {
        return new FileUploadResponseBuilder();
    }

    public static class FileUploadResponseBuilder {
        private String fileId;
        private String originalFileName;
        private String storedFileName;
        private FileType fileType;
        private String mimeType;
        private Long fileSize;
        private String fileUrl;
        private String thumbnailUrl;
        private StorageProvider storageProvider;
        private String resourceId;
        private String folderPath;
        private String uploadedBy;
        private LocalDateTime uploadedAt;
        private String description;
        private String[] tags;
        private String status;
        private String presignedUrl;

        public FileUploadResponseBuilder fileId(String fileId) { this.fileId = fileId; return this; }
        public FileUploadResponseBuilder originalFileName(String originalFileName) { this.originalFileName = originalFileName; return this; }
        public FileUploadResponseBuilder storedFileName(String storedFileName) { this.storedFileName = storedFileName; return this; }
        public FileUploadResponseBuilder fileType(FileType fileType) { this.fileType = fileType; return this; }
        public FileUploadResponseBuilder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public FileUploadResponseBuilder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public FileUploadResponseBuilder fileUrl(String fileUrl) { this.fileUrl = fileUrl; return this; }
        public FileUploadResponseBuilder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public FileUploadResponseBuilder storageProvider(StorageProvider storageProvider) { this.storageProvider = storageProvider; return this; }
        public FileUploadResponseBuilder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public FileUploadResponseBuilder folderPath(String folderPath) { this.folderPath = folderPath; return this; }
        public FileUploadResponseBuilder uploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; return this; }
        public FileUploadResponseBuilder uploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; return this; }
        public FileUploadResponseBuilder description(String description) { this.description = description; return this; }
        public FileUploadResponseBuilder tags(String[] tags) { this.tags = tags; return this; }
        public FileUploadResponseBuilder status(String status) { this.status = status; return this; }
        public FileUploadResponseBuilder presignedUrl(String presignedUrl) { this.presignedUrl = presignedUrl; return this; }

        public FileUploadResponse build() {
            return new FileUploadResponse(fileId, originalFileName, storedFileName, fileType, mimeType, fileSize, fileUrl, thumbnailUrl, storageProvider, resourceId, folderPath, uploadedBy, uploadedAt, description, tags, status, presignedUrl);
        }
    }
}
