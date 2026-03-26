package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.conversation.CreateConversationRequest;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.conversation.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import iuh.fit.utils.JwtUtils;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation", description = "Conversation management APIs")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    @Operation(summary = "Create a group conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> createGroup(
            @Valid @RequestBody CreateConversationRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.createGroupConversation(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tạo nhóm chat thành công"));
    }

    @GetMapping
    @Operation(summary = "Get all conversations of current user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getMyConversations() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<ConversationResponse> response = conversationService.getUserConversations(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách hội thoại thành công"));
    }

    @GetMapping("/private/{otherUserId}")
    @Operation(summary = "Get or create a private conversation with another user")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreatePrivate(
            @PathVariable String otherUserId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.getOrCreatePrivateConversation(userId, otherUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy hội thoại cá nhân thành công"));
    }

    @GetMapping("/p2p/{friendId}")
    @Operation(summary = "Find a P2P conversation. Returns null if not exists (Lazy Creation)")
    public ResponseEntity<ApiResponse<ConversationResponse>> getP2PConversation(
            @PathVariable String friendId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.getPrivateConversation(userId, friendId);
        return ResponseEntity
                .ok(ApiResponse.success(response, response != null ? "Tìm thấy hội thoại" : "Chưa có hội thoại"));
    }

    @GetMapping("/self")
    @Operation(summary = "Get or create self conversation (My Documents / Cloud)")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateSelf() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.getOrCreateSelfConversation(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy hội thoại Cloud thành công"));
    }

    // ==================== GROUP MEMBER MANAGEMENT ====================

    @GetMapping("/{conversationId}/members")
    @Operation(summary = "Get members of a group conversation")
    public ResponseEntity<ApiResponse<java.util.List<ConversationResponse.MemberInfo>>> getGroupMembers(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getGroupMembers(conversationId, userId),
                "Lấy danh sách thành viên thành công"));
    }

    @PostMapping("/{conversationId}/members")
    @Operation(summary = "Add members to a group (Admin/Deputy only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> addMembers(
            @PathVariable String conversationId,
            @RequestBody java.util.List<String> memberIds) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.addMembers(conversationId, memberIds, userId),
                "Thêm thành viên thành công"));
    }

    @DeleteMapping("/{conversationId}/members/{memberId}")
    @Operation(summary = "Remove a member from group (Admin only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> removeMember(
            @PathVariable String conversationId,
            @PathVariable String memberId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.removeMember(conversationId, memberId, userId),
                "Xóa thành viên thành công"));
    }

    @PostMapping("/{conversationId}/leave")
    @Operation(summary = "Leave a group conversation. Admin must provide successorId.")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @PathVariable String conversationId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String successorId = (body != null) ? body.get("successorId") : null;
        conversationService.leaveGroup(conversationId, userId, successorId);
        return ResponseEntity.ok(ApiResponse.success("Đã rời khỏi nhóm"));
    }

    // ==================== ROLE MANAGEMENT ====================

    @PatchMapping("/{conversationId}/members/{targetUserId}/role")
    @Operation(summary = "Change a member's role (Admin only). newRole: DEPUTY or MEMBER")
    public ResponseEntity<ApiResponse<ConversationResponse.MemberInfo>> changeMemberRole(
            @PathVariable String conversationId,
            @PathVariable String targetUserId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String role = body.get("role");
        if (role == null)
            return ResponseEntity.badRequest().build();
        iuh.fit.enums.MemberRole newRole = iuh.fit.enums.MemberRole.valueOf(role.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.changeMemberRole(conversationId, targetUserId, newRole, userId),
                "Thay đổi quyền thành công"));
    }

    @PostMapping("/{conversationId}/transfer")
    @Operation(summary = "Transfer ADMIN ownership to another member")
    public ResponseEntity<ApiResponse<Void>> transferOwnership(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String newAdminId = body.get("newAdminId");
        if (newAdminId == null)
            return ResponseEntity.badRequest().build();
        conversationService.transferOwnership(conversationId, newAdminId, userId);
        return ResponseEntity.ok(ApiResponse.success("Chuyển quyền Trưởng nhóm thành công"));
    }

    // ==================== PIN & SOFT DELETE ====================

    @PostMapping("/{conversationId}/pin")
    @Operation(summary = "Toggle pin/unpin a conversation for the current user")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> togglePin(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.togglePinConversation(conversationId, userId),
                "Cập nhật ghim hội thoại thành công"));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Soft-delete (hide) a conversation for the current user only")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> softDeleteConversation(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.softDeleteConversation(conversationId, userId),
                "Đã xóa hội thoại"));
    }

    // ==================== MESSAGE REQUEST (STRANGER) MANAGEMENT
    // ====================

    @GetMapping("/requests")
    @Operation(summary = "Get all pending message requests for the current user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getPendingMessageRequests() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getPendingMessageRequests(userId),
                "Lấy danh sách tin nhắn chờ thành công"));
    }

    @PostMapping("/{conversationId}/accept")
    @Operation(summary = "Accept a message request from a stranger")
    public ResponseEntity<ApiResponse<ConversationResponse>> acceptMessageRequest(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.acceptMessageRequest(conversationId, userId),
                "Đã chấp nhận tin nhắn"));
    }

    @PostMapping("/{conversationId}/block")
    @Operation(summary = "Block a stranger who sent a message request")
    public ResponseEntity<ApiResponse<Void>> blockMessageRequest(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        conversationService.blockMessageRequest(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã chặn người dùng này"));
    }

    @DeleteMapping("/{conversationId}/decline")
    @Operation(summary = "Decline and delete a message request")
    public ResponseEntity<ApiResponse<Void>> declineMessageRequest(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        conversationService.declineMessageRequest(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối tin nhắn"));
    }
}
