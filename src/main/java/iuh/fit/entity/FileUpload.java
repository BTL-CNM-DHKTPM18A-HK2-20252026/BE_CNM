package iuh.fit.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * FileUpload Entity
 * Lưu trữ thông tin về các file đã được upload (ảnh, video, tài liệu, v.v.)
 */
@Document(collection = "file_uploads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUpload {

    @Id
    String fileId;

    /**
     * Tên file gốc
     */
    String originalFileName;

    /**
     * Tên file đã được lưu (có thể khác với tên gốc)
     */
    String storedFileName;

    /**
     * Loại file (IMAGE, VIDEO, DOCUMENT, AUDIO, OTHER)
     */
    FileType fileType;

    /**
     * MIME type của file (image/jpeg, video/mp4, etc.)
     */
    String mimeType;

    /**
     * Kích thước file (bytes)
     */
    Long fileSize;

    /**
     * URL công khai để truy cập file
     */
    String fileUrl;

    /**
     * URL thumbnail (cho ảnh/video)
     */
    String thumbnailUrl;

    /**
     * Nơi lưu trữ file (CLOUDINARY, FIREBASE, LOCAL, S3, etc.)
     */
    StorageProvider storageProvider;

    /**
     * Public ID hoặc Resource ID từ storage provider
     */
    String resourceId;

    /**
     * Folder/path lưu trữ file
     */
    String folderPath;

    /**
     * ID người upload
     */
    String uploadedBy;

    /**
     * Thời gian upload
     */
    @CreatedDate
    LocalDateTime uploadedAt;

    /**
     * Mô tả về file
     */
    String description;

    /**
     * Tags để tìm kiếm
     */
    String[] tags;

    /**
     * Trạng thái file (ACTIVE, DELETED, ARCHIVED)
     */
    String status;

    /**
     * Metadata bổ sung (width, height cho ảnh, duration cho video, etc.)
     */
    String metadata;

    /**
     * Thời gian xóa (nếu có)
     */
    LocalDateTime deletedAt;
}
