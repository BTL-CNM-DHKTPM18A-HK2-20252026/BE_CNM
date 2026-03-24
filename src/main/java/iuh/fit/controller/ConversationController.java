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
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.createGroupConversation(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tạo nhóm chat thành công"));
    }

    @GetMapping
    @Operation(summary = "Get all conversations of current user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getMyConversations() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<ConversationResponse> response = conversationService.getUserConversations(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách hội thoại thành công"));
    }

    @GetMapping("/private/{otherUserId}")
    @Operation(summary = "Get or create a private conversation with another user")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreatePrivate(
            @PathVariable String otherUserId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.getOrCreatePrivateConversation(userId, otherUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy hội thoại cá nhân thành công"));
    }

    @GetMapping("/self")
    @Operation(summary = "Get or create self conversation (My Documents / Cloud)")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateSelf() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ConversationResponse response = conversationService.getOrCreateSelfConversation(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy hội thoại Cloud thành công"));
    }
}
