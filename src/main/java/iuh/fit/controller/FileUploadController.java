package iuh.fit.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.file.FileUploadRequest;
import iuh.fit.dto.response.file.FileUploadResponse;
import iuh.fit.service.file.FileStorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * File Upload Controller
 * Quản lý upload, download, xóa file
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "File Management", description = "API quản lý file uploads (ảnh, video, tài liệu)")
public class FileUploadController {

    FileStorageService fileStorageService;

    /**
     * Upload một file
     */
    @Operation(summary = "Upload một file", 
               description = "Upload file (ảnh, video, tài liệu) lên Cloudinary hoặc dịch vụ lưu trữ khác")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload thành công",
                    content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "File không hợp lệ",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderPath", required = false, defaultValue = "general") String folderPath,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String[] tags,
            @RequestParam(value = "generateThumbnail", required = false, defaultValue = "false") Boolean generateThumbnail,
            @RequestParam(value = "optimize", required = false, defaultValue = "true") Boolean optimize) {

        // TODO: Get userId from JWT token
        String userId = "temp-user-id"; // Temporary

        FileUploadRequest request = FileUploadRequest.builder()
                .folderPath(folderPath)
                .description(description)
                .tags(tags)
                .generateThumbnail(generateThumbnail)
                .optimize(optimize)
                .build();

        log.info("Upload file request: {} by user: {}", file.getOriginalFilename(), userId);
        FileUploadResponse response = fileStorageService.uploadFile(file, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Upload nhiều file cùng lúc
     */
    @Operation(summary = "Upload nhiều file", 
               description = "Upload nhiều file cùng lúc lên Cloudinary")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload thành công"),
            @ApiResponse(responseCode = "400", description = "File không hợp lệ",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileUploadResponse>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folderPath", required = false, defaultValue = "general") String folderPath,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String[] tags,
            @RequestParam(value = "generateThumbnail", required = false, defaultValue = "false") Boolean generateThumbnail,
            @RequestParam(value = "optimize", required = false, defaultValue = "true") Boolean optimize) {

        // TODO: Get userId from JWT token
        String userId = "temp-user-id";

        FileUploadRequest request = FileUploadRequest.builder()
                .folderPath(folderPath)
                .description(description)
                .tags(tags)
                .generateThumbnail(generateThumbnail)
                .optimize(optimize)
                .build();

        log.info("Upload {} files by user: {}", files.size(), userId);
        List<FileUploadResponse> responses = fileStorageService.uploadMultipleFiles(files, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /**
     * Lấy thông tin file theo ID
     */
    @Operation(summary = "Lấy thông tin file", description = "Lấy thông tin chi tiết của một file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy file",
                    content = @Content)
    })
    @GetMapping("/{fileId}")
    public ResponseEntity<FileUploadResponse> getFileById(@PathVariable String fileId) {
        log.info("Get file request: {}", fileId);
        FileUploadResponse response = fileStorageService.getFileById(fileId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách file của user hiện tại
     */
    @Operation(summary = "Lấy danh sách file của user", 
               description = "Lấy tất cả file đã upload của user hiện tại")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my-files")
    public ResponseEntity<List<FileUploadResponse>> getMyFiles() {
        // TODO: Get userId from JWT token
        String userId = "temp-user-id";

        log.info("Get files for user: {}", userId);
        List<FileUploadResponse> responses = fileStorageService.getFilesByUserId(userId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Xóa file
     */
    @Operation(summary = "Xóa file", description = "Xóa file đã upload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Xóa thành công"),
            @ApiResponse(responseCode = "403", description = "Không có quyền xóa file này",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy file",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileId) {
        // TODO: Get userId from JWT token
        String userId = "temp-user-id";

        log.info("Delete file request: {} by user: {}", fileId, userId);
        boolean deleted = fileStorageService.deleteFile(fileId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", deleted);
        response.put("message", deleted ? "Xóa file thành công" : "Không thể xóa file");
        response.put("fileId", fileId);

        return ResponseEntity.ok(response);
    }

    /**
     * Xóa nhiều file
     */
    @Operation(summary = "Xóa nhiều file", description = "Xóa nhiều file cùng lúc")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Xóa thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> deleteMultipleFiles(@RequestBody List<String> fileIds) {
        // TODO: Get userId from JWT token
        String userId = "temp-user-id";

        log.info("Delete {} files by user: {}", fileIds.size(), userId);
        int deletedCount = fileStorageService.deleteMultipleFiles(fileIds, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("totalRequested", fileIds.size());
        response.put("deletedCount", deletedCount);
        response.put("message", String.format("Đã xóa %d/%d file", deletedCount, fileIds.size()));

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy URL tạm thời để truy cập file
     */
    @Operation(summary = "Lấy URL tạm thời", 
               description = "Lấy URL tạm thời để truy cập file (cho private file)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy file",
                    content = @Content)
    })
    @GetMapping("/{fileId}/temporary-url")
    public ResponseEntity<Map<String, String>> getTemporaryUrl(
            @PathVariable String fileId,
            @RequestParam(value = "duration", defaultValue = "3600") int duration) {

        log.info("Get temporary URL for file: {}", fileId);
        String url = fileStorageService.getTemporaryUrl(fileId, duration);

        Map<String, String> response = new HashMap<>();
        response.put("fileId", fileId);
        response.put("url", url);
        response.put("expiresIn", duration + " seconds");

        return ResponseEntity.ok(response);
    }
}
