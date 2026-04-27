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
@Tag(name = "File Management", description = "API for managing file uploads (images, videos, documents)")
public class FileController {

    final FileStorageService fileStorageService;

    /**
     * Tạo Pre-signed URL để upload file trực tiếp lên S3
     */
    @Operation(summary = "Generate pre-signed URL", 
               description = "Get a pre-signed PUT URL to upload a file directly to AWS S3 from the client")
    @GetMapping("/presigned-url")
    public ResponseEntity<FileUploadResponse> generatePresignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType,
            @RequestParam(value = "folderPath", required = false, defaultValue = "general") String folderPath) {
        
        log.info("Generate presigned URL for file: {} in folder: {}", fileName, folderPath);
        FileUploadResponse response = fileStorageService.generateUploadUrl(fileName, contentType, folderPath);
        return ResponseEntity.ok(response);
    }

    /**
     * Lưu metadata file sau khi đã upload lên S3 thành công
     */
    @Operation(summary = "Save file metadata", 
               description = "Save file metadata to database after successful direct upload to S3")
    @PostMapping("/metadata")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<FileUploadResponse> saveFileMetadata(@RequestBody iuh.fit.dto.request.file.FileUploadCompleteRequest metadataRequest) {
        String userId = iuh.fit.utils.JwtUtils.getCurrentUserId();
        if (userId == null) userId = "temp-user-id";

        log.info("Save file metadata: {} for user: {}", metadataRequest.getOriginalFileName() != null ? metadataRequest.getOriginalFileName() : metadataRequest.getOriginalName(), userId);
        FileUploadResponse response = fileStorageService.saveFileMetadata(metadataRequest, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Upload một file (Proxy qua Server)
     */
    @Operation(summary = "Upload a file", 
               description = "Upload file (image, video, document) to AWS S3 via Server")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload successful",
                    content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
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

        String userId = iuh.fit.utils.JwtUtils.getCurrentUserId();
        if (userId == null) userId = "temp-user-id";

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
    @Operation(summary = "Upload multiple files", 
               description = "Upload multiple files at once to AWS S3 via Server")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload successful"),
            @ApiResponse(responseCode = "400", description = "Invalid file",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
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

        String userId = iuh.fit.utils.JwtUtils.getCurrentUserId();
        if (userId == null) userId = "temp-user-id";

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
    @Operation(summary = "Get file info", description = "Get detailed information of a file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success",
                    content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
            @ApiResponse(responseCode = "404", description = "File not found",
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
    @Operation(summary = "Get user file list", 
               description = "Get all uploaded files of the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my-files")
    public ResponseEntity<List<FileUploadResponse>> getMyFiles() {
        String userId = iuh.fit.utils.JwtUtils.getCurrentUserId();
        if (userId == null) userId = "temp-user-id";

        log.info("Get files for user: {}", userId);
        List<FileUploadResponse> responses = fileStorageService.getFilesByUserId(userId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Xóa file
     */
    @Operation(summary = "Delete file", description = "Delete an uploaded file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Delete successful"),
            @ApiResponse(responseCode = "403", description = "Forbidden: No permission to delete this file",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "File not found",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileId) {
        String userId = iuh.fit.utils.JwtUtils.getCurrentUserId();
        if (userId == null) userId = "temp-user-id";

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
    @Operation(summary = "Delete multiple files", description = "Delete multiple files at once")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Delete successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> deleteMultipleFiles(@RequestBody List<String> fileIds) {
        String userId = iuh.fit.utils.JwtUtils.getCurrentUserId();
        if (userId == null) userId = "temp-user-id";

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
    @Operation(summary = "Get temporary URL", 
               description = "Get a temporary URL to access a file (for private files)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "File not found",
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
