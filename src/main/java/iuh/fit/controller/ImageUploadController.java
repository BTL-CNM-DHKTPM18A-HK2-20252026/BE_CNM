package iuh.fit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.image.ImageUploadCompleteRequest;
import iuh.fit.dto.response.image.ImagePresignedUrlResponse;
import iuh.fit.dto.response.image.ImageUploadMetadataResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.image.ImageUploadService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Image Upload", description = "Generate S3 pre-signed URL and save upload metadata")
public class ImageUploadController {

    ImageUploadService imageUploadService;

    @GetMapping("/presigned-url")
    @Operation(summary = "Generate pre-signed PUT URL for image upload")
    public ResponseEntity<ApiResponse<ImagePresignedUrlResponse>> generatePresignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {

        ImagePresignedUrlResponse response = imageUploadService.generatePutPresignedUrl(fileName, contentType);
        return ResponseEntity.ok(ApiResponse.success(response, "Tạo pre-signed URL thành công"));
    }

    @PostMapping("/save")
    @Operation(summary = "Save image metadata after upload success")
    public ResponseEntity<ApiResponse<ImageUploadMetadataResponse>> saveUpload(
            @Valid @RequestBody ImageUploadCompleteRequest request) {

        ImageUploadMetadataResponse response = imageUploadService.saveUploadedImageMetadata(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Lưu metadata ảnh thành công"));
    }

    @PostMapping("/complete")
    @Operation(summary = "Backward-compatible alias for /images/save")
    public ResponseEntity<ApiResponse<ImageUploadMetadataResponse>> completeUpload(
            @Valid @RequestBody ImageUploadCompleteRequest request) {
        return saveUpload(request);
    }
}
