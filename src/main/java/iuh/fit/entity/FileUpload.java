package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;

/**
 * FileUpload Entity
 * Lưu trữ thông tin về các file đã được upload (ảnh, video, tài liệu, v.v.)
 */
@Document(collection = "file_uploads")
public class FileUpload {

    @Id
    private String fileId = UUID.randomUUID().toString();
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
    @CreatedDate
    private LocalDateTime uploadedAt;
    private String description;
    private String[] tags;
    private String status;
    private String metadata;
    private LocalDateTime deletedAt;

    public FileUpload() {}

    public FileUpload(String fileId, String originalFileName, String storedFileName, FileType fileType, String mimeType, 
                     Long fileSize, String fileUrl, String thumbnailUrl, StorageProvider storageProvider, 
                     String resourceId, String folderPath, String uploadedBy, LocalDateTime uploadedAt, 
                     String description, String[] tags, String status, String metadata, LocalDateTime deletedAt) {
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
        this.metadata = metadata;
        this.deletedAt = deletedAt;
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
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public static FileUploadBuilder builder() {
        return new FileUploadBuilder();
    }

    public static class FileUploadBuilder {
        private String fileId = UUID.randomUUID().toString();
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
        private String metadata;
        private LocalDateTime deletedAt;

        public FileUploadBuilder fileId(String fileId) { this.fileId = fileId; return this; }
        public FileUploadBuilder originalFileName(String originalFileName) { this.originalFileName = originalFileName; return this; }
        public FileUploadBuilder storedFileName(String storedFileName) { this.storedFileName = storedFileName; return this; }
        public FileUploadBuilder fileType(FileType fileType) { this.fileType = fileType; return this; }
        public FileUploadBuilder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public FileUploadBuilder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public FileUploadBuilder fileUrl(String fileUrl) { this.fileUrl = fileUrl; return this; }
        public FileUploadBuilder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public FileUploadBuilder storageProvider(StorageProvider storageProvider) { this.storageProvider = storageProvider; return this; }
        public FileUploadBuilder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public FileUploadBuilder folderPath(String folderPath) { this.folderPath = folderPath; return this; }
        public FileUploadBuilder uploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; return this; }
        public FileUploadBuilder uploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; return this; }
        public FileUploadBuilder description(String description) { this.description = description; return this; }
        public FileUploadBuilder tags(String[] tags) { this.tags = tags; return this; }
        public FileUploadBuilder status(String status) { this.status = status; return this; }
        public FileUploadBuilder metadata(String metadata) { this.metadata = metadata; return this; }
        public FileUploadBuilder deletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }

        public FileUpload build() {
            return new FileUpload(fileId, originalFileName, storedFileName, fileType, mimeType, fileSize, fileUrl, thumbnailUrl, storageProvider, resourceId, folderPath, uploadedBy, uploadedAt, description, tags, status, metadata, deletedAt);
        }
    }
}
