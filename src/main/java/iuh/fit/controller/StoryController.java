package iuh.fit.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.story.CreateStoryRequest;
import iuh.fit.dto.response.story.StoryResponse;
import iuh.fit.service.story.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
@Tag(name = "Story", description = "Story management APIs")
public class StoryController {
    
    private final StoryService storyService;
    
    @PostMapping
    @Operation(summary = "Create a new story")
    public ResponseEntity<StoryResponse> createStory(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateStoryRequest request) {
        return ResponseEntity.ok(storyService.createStory(userId, request));
    }
    
    @GetMapping("/active")
    @Operation(summary = "Get active stories from friends")
    public ResponseEntity<List<StoryResponse>> getActiveStories(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam List<String> friendIds) {
        return ResponseEntity.ok(storyService.getActiveStories(userId, friendIds));
    }

    @GetMapping("/feed")
    @Operation(summary = "Get story feed (friends + self)")
    public ResponseEntity<List<StoryResponse>> getStoryFeed(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) List<String> friendIds) {
        if (friendIds == null) friendIds = List.of();
        return ResponseEntity.ok(storyService.getStoryFeed(userId, friendIds));
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's active stories")
    public ResponseEntity<List<StoryResponse>> getUserStories(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId) {
        return ResponseEntity.ok(storyService.getUserStories(userId, currentUserId));
    }
    
    @PostMapping("/{storyId}/view")
    @Operation(summary = "Mark story as viewed")
    public ResponseEntity<Void> viewStory(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId) {
        storyService.viewStory(storyId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{storyId}/react")
    @Operation(summary = "React to a story")
    public ResponseEntity<Void> reactToStory(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody iuh.fit.dto.request.story.ReactToStoryRequest request) {
        storyService.reactToStory(storyId, userId, request.getReaction());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{storyId}/reply")
    @Operation(summary = "Reply to a story via message")
    public ResponseEntity<Void> replyToStory(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody iuh.fit.dto.request.story.ReplyToStoryRequest request) {
        storyService.replyToStory(storyId, userId, request.getContent());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{storyId}/viewers")
    @Operation(summary = "Get list of story viewers")
    public ResponseEntity<List<iuh.fit.dto.response.story.StoryViewerResponse>> getStoryViewers(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(storyService.getStoryViewers(storyId, userId));
    }

    @DeleteMapping("/{storyId}")
    @Operation(summary = "Delete a story")
    public ResponseEntity<Void> deleteStory(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId) {
        storyService.deleteStory(storyId, userId);
        return ResponseEntity.noContent().build();
    }
}
