package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.conversation.CreateConversationRequest;
import iuh.fit.dto.request.conversation.HideConversationRequest;
import iuh.fit.dto.request.conversation.UpdateConversationRequest;
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
    private final iuh.fit.repository.ConversationMemberRepository conversationMemberRepository;

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

    // ==================== GROUP INFO UPDATE ====================

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update group conversation info (name, avatar, description). Admin/Deputy only.")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateGroupInfo(
            @PathVariable String conversationId,
            @Valid @RequestBody UpdateConversationRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.updateGroupInfo(conversationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin nhóm thành công"));
    }

    @PatchMapping("/{conversationId}/permissions")
    @Operation(summary = "Update group conversation permissions (canEditInfo, canPinMessages, etc.). Admin/Deputy only.")
    public ResponseEntity<ApiResponse<ConversationResponse>> updatePermissions(
            @PathVariable String conversationId,
            @Valid @RequestBody iuh.fit.dto.request.conversation.UpdatePermissionRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.updatePermissions(conversationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật quyền hạn nhóm thành công"));
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

    @DeleteMapping("/{conversationId}/dissolve")
    @Operation(summary = "Dissolve (disband) a group conversation. Admin only. Removes all members.")
    public ResponseEntity<ApiResponse<Void>> dissolveGroup(@PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        conversationService.dissolveGroup(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã giải tán nhóm"));
    }

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

    // ==================== NICKNAME MANAGEMENT ====================

    @PatchMapping("/{conversationId}/nickname")
    @Operation(summary = "Update nickname for the current user in a conversation")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> updateNickname(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String nickname = body.get("nickname");
        String updated = conversationService.updateNickname(conversationId, userId, nickname);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("conversationId", conversationId);
        result.put("nickname", updated);
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật biệt danh thành công"));
    }

    // ==================== CONVERSATION TAG ====================

    @PatchMapping("/{conversationId}/tag")
    @Operation(summary = "Update conversation tag for the current user (customer, family, work, friends, reply_later, colleagues)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> updateConversationTag(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String tag = body.get("tag");
        String updated = conversationService.updateConversationTag(conversationId, userId, tag);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("conversationId", conversationId);
        result.put("tag", updated);
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật phân loại thành công"));
    }

    // ==================== READ STATUS ====================

    @GetMapping("/{conversationId}/read-status")
    @Operation(summary = "Get read status of all members in a conversation")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getReadStatus(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getReadStatus(conversationId, userId),
                "Lấy trạng thái đã xem thành công"));
    }

    // ==================== MARK AS READ ====================

    @PatchMapping("/{conversationId}/mark-as-read")
    @Operation(summary = "Mark all messages in a conversation as read for the current user")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        conversationService.markAsRead(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đánh dấu đã đọc"));
    }

    // ==================== MUTE CONVERSATION ====================

    @PostMapping("/{conversationId}/mute")
    @Operation(summary = "Mute/unmute a conversation for the current user")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> muteConversation(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String duration = body.get("duration"); // 1h, 4h, until_8am, forever, off
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.muteConversation(conversationId, userId, duration),
                "Cập nhật tắt thông báo thành công"));
    }

    // ==================== MARK AS UNREAD ====================

    @PostMapping("/{conversationId}/mark-unread")
    @Operation(summary = "Toggle mark/unmark a conversation as unread for the current user")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> toggleMarkUnread(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.toggleMarkUnread(conversationId, userId),
                "Cập nhật đánh dấu chưa đọc thành công"));
    }

    // ==================== AUTO-DELETE MESSAGES ====================

    @PatchMapping("/{conversationId}/auto-delete")
    @Operation(summary = "Set auto-delete duration for messages in a conversation")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> updateAutoDeleteDuration(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String duration = body.get("duration"); // off, 1d, 7d, 30d
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.updateAutoDeleteDuration(conversationId, userId, duration),
                "Cập nhật tin nhắn tự xóa thành công"));
    }

    // ==================== HIDE / UNHIDE CONVERSATION ====================

    // ==================== CHAT WALLPAPER ====================

    @PatchMapping("/{conversationId}/wallpaper")
    @Operation(summary = "Set chat wallpaper for a specific conversation (per user)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> updateWallpaper(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String wallpaperUrl = body.get("wallpaperUrl"); // null or empty to reset
        java.util.Optional<iuh.fit.entity.ConversationMember> optMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId);
        if (optMember.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        iuh.fit.entity.ConversationMember member = optMember.get();
        member.setWallpaperUrl(wallpaperUrl == null || wallpaperUrl.isBlank() ? null : wallpaperUrl);
        conversationMemberRepository.save(member);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("conversationId", conversationId);
        result.put("wallpaperUrl", member.getWallpaperUrl());
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật hình nền chat thành công"));
    }

    // ==================== HIDE / UNHIDE CONVERSATION ====================

    @GetMapping("/hidden")
    @Operation(summary = "Get all hidden conversations for the current user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getHiddenConversations() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getHiddenConversations(userId),
                "Lấy danh sách hội thoại đã ẩn thành công"));
    }

    @PostMapping("/{conversationId}/unhide")
    @Operation(summary = "Unhide a previously hidden conversation for the current user (requires PIN)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> unhideConversation(
            @PathVariable String conversationId,
            @Valid @RequestBody HideConversationRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.unhideConversation(conversationId, userId, request.getPinCode()),
                "Đã hiện lại hội thoại"));
    }

    // ==================== PIN-PROTECTED HIDE ====================

    @PostMapping("/{conversationId}/hide")
    @Operation(summary = "Hide conversation with PIN verification")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> hideConversationWithPin(
            @PathVariable String conversationId,
            @Valid @RequestBody HideConversationRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.hideConversationWithPin(conversationId, userId, request.getPinCode()),
                "Đã ẩn hội thoại"));
    }

    @GetMapping("/hidden/search")
    @Operation(summary = "Search hidden conversations (requires PIN)")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> searchHiddenConversations(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam String pinCode) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<ConversationResponse> results = conversationService.searchHiddenConversations(userId, q, pinCode);
        return ResponseEntity.ok(ApiResponse.success(results, "OK"));
    }

    @PostMapping("/join/{conversationId}")
    @Operation(summary = "Join a group conversation via invitation link/ID")
    public ResponseEntity<ApiResponse<ConversationResponse>> joinGroup(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.joinGroup(conversationId, userId),
                "Tham gia nhóm thành công"));
    }

    @GetMapping("/join/{conversationId}/preview")
    @Operation(summary = "Get basic group info for invitation link preview (Public)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getGroupPreview(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getGroupPreview(conversationId),
                "Lấy thông tin xem trước nhóm thành công"));
    }
}
