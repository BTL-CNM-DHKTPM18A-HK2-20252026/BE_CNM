package iuh.fit.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import iuh.fit.enums.ReactionType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.post.CreateCommentRequest;
import iuh.fit.dto.response.post.CommentResponse;
import iuh.fit.exception.UnauthorizedException;
import iuh.fit.service.post.PostCommentService;
import iuh.fit.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Comment", description = "Comment management APIs")
public class PostCommentController {

    private final PostCommentService postCommentService;

    @PostMapping("/{postId}/comments")
    @Operation(summary = "Add a comment to a post")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(postCommentService.addComment(postId, getCurrentUserId(), request));
    }

    @GetMapping("/{postId}/comments")
    @Operation(summary = "Get all comments for a post")
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable String postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(postCommentService.getCommentsByPostId(postId, userId));
    }

    @PostMapping("/{postId}/comments/{commentId}/react/{type}")
    @Operation(summary = "React to a comment")
    public ResponseEntity<CommentResponse> reactToComment(
            @PathVariable String postId,
            @PathVariable String commentId,
            @PathVariable ReactionType type) {
        return ResponseEntity.ok(postCommentService.reactToComment(commentId, getCurrentUserId(), type));
    }

    private String getCurrentUserId() {
        String currentUserId = JwtUtils.getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw UnauthorizedException.notAuthenticated();
        }
        return currentUserId;
    }
}
