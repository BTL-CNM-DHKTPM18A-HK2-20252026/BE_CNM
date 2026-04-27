package iuh.fit.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.FileUpload;
import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;

@Repository
public interface FileUploadRepository extends MongoRepository<FileUpload, String> {

    /**
     * Tìm file theo uploadedBy
     */
    List<FileUpload> findByUploadedBy(String uploadedBy);

    /**
     * Tìm file theo uploadedBy, sắp xếp theo thời gian upload mới nhất
     */
    List<FileUpload> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    /**
     * Tìm file theo loại file
     */
    List<FileUpload> findByFileType(FileType fileType);

    /**
     * Tìm file theo storage provider
     */
    List<FileUpload> findByStorageProvider(StorageProvider storageProvider);

    /**
     * Tìm file theo resourceId
     */
    Optional<FileUpload> findByResourceId(String resourceId);

    /**
     * Tìm file theo uploadedBy và fileType
     */
    List<FileUpload> findByUploadedByAndFileType(String uploadedBy, FileType fileType);

    /**
     * Tìm file theo uploadedBy và status
     */
    List<FileUpload> findByUploadedByAndStatus(String uploadedBy, String status);

    /**
     * Tìm file upload trong khoảng thời gian
     */
    List<FileUpload> findByUploadedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Tìm file theo folderPath
     */
    List<FileUpload> findByFolderPath(String folderPath);

    /**
     * Tìm file theo tags
     */
    List<FileUpload> findByTagsContaining(String tag);

    /**
     * Đếm số file của user
     */
    long countByUploadedBy(String uploadedBy);

    /**
     * Tính tổng kích thước file của user
     */
    // Sẽ cần custom query hoặc aggregation
}
