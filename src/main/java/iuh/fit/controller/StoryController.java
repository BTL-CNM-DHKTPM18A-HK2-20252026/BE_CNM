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

    private final StoryService service;

    @PostMapping
    public ResponseEntity<StoryResponse> create(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateStoryRequest req
    ) {
        return ResponseEntity.ok(service.createStory(userId, req));
    }

    @GetMapping("/active")
    public ResponseEntity<List<StoryResponse>> getActive(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam List<String> friendIds
    ) {
        return ResponseEntity.ok(service.getActiveStories(userId, friendIds));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<StoryResponse>> getUserStories(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId
    ) {
        return ResponseEntity.ok(service.getUserStories(userId, currentUserId));
    }

    @GetMapping("/archive")
    public ResponseEntity<List<StoryResponse>> getArchivedStories(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(service.getArchivedStories(userId));
    }

    @PostMapping("/{storyId}/view")
    public ResponseEntity<Void> view(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String viewerId
    ) {
        service.viewStory(storyId, viewerId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> delete(
            @PathVariable String storyId,
            @RequestHeader("X-User-Id") String userId
    ) {
        service.deleteStory(storyId, userId);
        return ResponseEntity.noContent().build();
    }
}
