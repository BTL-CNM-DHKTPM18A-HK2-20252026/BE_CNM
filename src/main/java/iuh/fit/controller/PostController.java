package iuh.fit.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.post.CreatePostRequest;
import iuh.fit.dto.request.post.UpdatePostRequest;
import iuh.fit.dto.response.post.PostResponse;
import iuh.fit.service.post.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Post", description = "Post management APIs")
public class PostController {
    
    private final PostService postService;
    
    @PostMapping
    @Operation(summary = "Create a new post")
    public ResponseEntity<PostResponse> createPost(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.ok(postService.createPost(userId, request));
    }
    
    @GetMapping("/{postId}")
    @Operation(summary = "Get a post by ID")
    public ResponseEntity<PostResponse> getPostById(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(postService.getPostById(postId, userId));
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get posts by user")
    public ResponseEntity<Page<PostResponse>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(postService.getUserPosts(userId, pageable));
    }
    
    @GetMapping("/feed")
    @Operation(summary = "Get news feed")
    public ResponseEntity<Page<PostResponse>> getNewsFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(postService.getNewsFeed(pageable));
    }
    
    @PutMapping("/{postId}")
    @Operation(summary = "Update a post")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdatePostRequest request) {
        return ResponseEntity.ok(postService.updatePost(postId, request, userId));
    }
    
    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete a post")
    public ResponseEntity<Void> deletePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId) {
        postService.deletePost(postId, userId);
        return ResponseEntity.noContent().build();
    }
}
