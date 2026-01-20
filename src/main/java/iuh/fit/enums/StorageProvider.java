package iuh.fit.enums;

/**
 * StorageProvider Enum
 * Các nhà cung cấp dịch vụ lưu trữ file
 */
public enum StorageProvider {
    CLOUDINARY,     // Cloudinary service
    FIREBASE,       // Firebase Storage
    AWS_S3,         // Amazon S3
    AZURE_BLOB,     // Azure Blob Storage
    GOOGLE_DRIVE,   // Google Drive
    LOCAL,          // Local file system
    OTHER           // Các dịch vụ khác
}
