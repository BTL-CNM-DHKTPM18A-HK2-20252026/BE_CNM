package iuh.fit.dto.request.file;

import iuh.fit.enums.FileType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * File Upload Request DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUploadRequest {

    /**
     * Folder path để lưu file (posts, avatars, messages, etc.)
     */
    String folderPath;

    /**
     * Loại file (optional, sẽ tự detect nếu không có)
     */
    FileType fileType;

    /**
     * Mô tả file
     */
    String description;

    /**
     * Tags để tìm kiếm
     */
    String[] tags;

    /**
     * Có tạo thumbnail không (cho ảnh/video)
     */
    Boolean generateThumbnail;

    /**
     * Có optimize file không (nén ảnh, chuyển format, etc.)
     */
    Boolean optimize;
}
