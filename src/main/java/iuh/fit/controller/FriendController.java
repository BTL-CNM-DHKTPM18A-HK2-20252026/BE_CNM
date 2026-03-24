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
import iuh.fit.dto.request.friend.FriendActionRequest;
import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.friend.FriendService;
import iuh.fit.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
@Tag(name = "Friend", description = "Friend management APIs")
public class FriendController {
    
    private final FriendService friendService;
    
    @GetMapping
    @Operation(summary = "Get friends list")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getFriendsList() {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(friendService.getFriendsList(userId)));
    }

    @PostMapping("/request")
    @Operation(summary = "Send friend request")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> sendFriendRequest(
            @Valid @RequestBody FriendActionRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(friendService.sendFriendRequest(userId, request.getUserId()), "Đã gửi lời mời kết bạn"));
    }
    
    @GetMapping("/requests/received")
    @Operation(summary = "Get received friend requests")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> getReceivedRequests() {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(friendService.getReceivedRequests(userId)));
    }

    @GetMapping("/requests/sent")
    @Operation(summary = "Get sent friend requests")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> getSentRequests() {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(friendService.getSentRequests(userId)));
    }
    
    @PutMapping("/request/{requestId}/accept")
    @Operation(summary = "Accept friend request")
    public ResponseEntity<ApiResponse<Void>> acceptFriendRequest(
            @PathVariable String requestId) {
        String userId = JwtUtils.getCurrentUserId();
        friendService.acceptFriendRequest(requestId, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã chấp nhận lời mời kết bạn"));
    }
    
    @PutMapping("/request/{requestId}/reject")
    @Operation(summary = "Reject friend request")
    public ResponseEntity<ApiResponse<Void>> rejectFriendRequest(
            @PathVariable String requestId) {
        String userId = JwtUtils.getCurrentUserId();
        friendService.rejectFriendRequest(requestId, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối lời mời kết bạn"));
    }
    
    @DeleteMapping("/unfriend")
    @Operation(summary = "Unfriend a user")
    public ResponseEntity<ApiResponse<Void>> unfriend(
            @Valid @RequestBody FriendActionRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        friendService.unfriend(userId, request.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bạn bè/lời mời"));
    }
}
