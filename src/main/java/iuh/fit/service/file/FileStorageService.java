package iuh.fit.service.file;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import iuh.fit.dto.request.file.FileUploadRequest;
import iuh.fit.dto.response.file.FileUploadResponse;

/**
 * File Storage Service Interface
 * Interface để quản lý upload/download/delete file
 */
public interface FileStorageService {

    /**
     * Upload một file
     *
     * @param file    MultipartFile cần upload
     * @param request Upload request với các options
     * @param userId  ID người upload
     * @return FileUploadResponse
     */
    FileUploadResponse uploadFile(MultipartFile file, FileUploadRequest request, String userId);

    /**
     * Upload nhiều file cùng lúc
     *
     * @param files   Danh sách file cần upload
     * @param request Upload request với các options
     * @param userId  ID người upload
     * @return Danh sách FileUploadResponse
     */
    List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, FileUploadRequest request, String userId);

    /**
     * Lấy thông tin file theo ID
     *
     * @param fileId ID của file
     * @return FileUploadResponse
     */
    FileUploadResponse getFileById(String fileId);

    /**
     * Lấy danh sách file của user
     *
     * @param userId ID của user
     * @return Danh sách FileUploadResponse
     */
    List<FileUploadResponse> getFilesByUserId(String userId);

    /**
     * Xóa file
     *
     * @param fileId ID của file cần xóa
     * @param userId ID người thực hiện xóa
     * @return true nếu xóa thành công
     */
    boolean deleteFile(String fileId, String userId);

    /**
     * Xóa nhiều file
     *
     * @param fileIds Danh sách ID file cần xóa
     * @param userId  ID người thực hiện xóa
     * @return Số lượng file đã xóa thành công
     */
    int deleteMultipleFiles(List<String> fileIds, String userId);

    /**
     * Lấy URL tạm thời để truy cập file (cho private file)
     *
     * @param fileId   ID của file
     * @param duration Thời gian tồn tại của URL (giây)
     * @return URL tạm thời
     */
    String getTemporaryUrl(String fileId, int duration);
}
