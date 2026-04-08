package iuh.fit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import iuh.fit.dto.response.utils.LinkPreviewDto;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.utils.LinkPreviewService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/utils")
@RequiredArgsConstructor
public class UtilsController {

    private final LinkPreviewService linkPreviewService;

    @GetMapping("/link-preview")
    public ResponseEntity<ApiResponse<LinkPreviewDto>> getLinkPreview(@RequestParam String url) {
        LinkPreviewDto preview = linkPreviewService.getPreview(url);
        return ResponseEntity.ok(ApiResponse.success(preview, "Lấy link preview thành công"));
    }
}
