package iuh.fit.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.FriendActionRequest;
import iuh.fit.dto.response.FriendRequestResponse;
import iuh.fit.service.friend.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
@Tag(name = "Friend", description = "Friend management APIs")
public class FriendController {
    
    private final FriendService friendService;
    
    @PostMapping("/request")
    @Operation(summary = "Send friend request")
    public ResponseEntity<FriendRequestResponse> sendFriendRequest(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody FriendActionRequest request) {
        return ResponseEntity.ok(friendService.sendFriendRequest(userId, request.getUserId()));
    }
    
    @GetMapping("/requests/pending")
    @Operation(summary = "Get pending friend requests")
    public ResponseEntity<List<FriendRequestResponse>> getPendingRequests(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(friendService.getPendingRequests(userId));
    }
    
    @PutMapping("/request/{requestId}/accept")
    @Operation(summary = "Accept friend request")
    public ResponseEntity<Void> acceptFriendRequest(
            @PathVariable String requestId,
            @RequestHeader("X-User-Id") String userId) {
        friendService.acceptFriendRequest(requestId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/request/{requestId}/reject")
    @Operation(summary = "Reject friend request")
    public ResponseEntity<Void> rejectFriendRequest(
            @PathVariable String requestId,
            @RequestHeader("X-User-Id") String userId) {
        friendService.rejectFriendRequest(requestId, userId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/unfriend")
    @Operation(summary = "Unfriend a user")
    public ResponseEntity<Void> unfriend(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody FriendActionRequest request) {
        friendService.unfriend(userId, request.getUserId());
        return ResponseEntity.noContent().build();
    }
}
