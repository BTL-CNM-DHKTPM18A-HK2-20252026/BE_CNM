package iuh.fit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.interaction.ReelWatchRequest;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.recommendation.RecommendationService;
import iuh.fit.utils.JwtUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/interactions")
@RequiredArgsConstructor
@Tag(name = "Interaction", description = "User interaction tracking APIs")
public class InteractionController {

    private final RecommendationService recommendationService;

    @PostMapping("/post-view")
    @Operation(summary = "Track post view for recommendation engine")
    public ResponseEntity<ApiResponse<Void>> trackPostView(@RequestBody PostViewRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        recommendationService.trackInteraction(userId, request.getPostId(), "VIEW", 1.0);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/reel-watch")
    @Operation(summary = "Track reel watch progress")
    public ResponseEntity<ApiResponse<Void>> trackReelWatch(@RequestBody ReelWatchRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        recommendationService.trackInteraction(userId, request.getReelId(), "WATCH", request.getWatchedDuration());
        if (Boolean.TRUE.equals(request.getIsCompleted())) {
            recommendationService.trackInteraction(userId, request.getReelId(), "COMPLETED", 1.0);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Data
    public static class PostViewRequest {
        private String postId;
    }
}
