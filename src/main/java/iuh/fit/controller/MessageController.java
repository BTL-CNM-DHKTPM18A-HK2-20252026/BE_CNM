package iuh.fit.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import iuh.fit.utils.JwtUtils;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.message.ChatWithAiRequest;
import iuh.fit.dto.request.message.SendMessageRequest;
import iuh.fit.dto.request.message.ReactMessageRequest;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.dto.response.message.AiChatResponse;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.service.ai.AiChatService;
import iuh.fit.service.message.MessageService;
import iuh.fit.service.s3.S3Service;
import iuh.fit.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Message", description = "Message management APIs")
public class MessageController {

    private final MessageService messageService;
    private final AiChatService aiChatService;
    private final S3Service s3Service;

    @PostMapping
    @Operation(summary = "Send a message (Supports Lazy Creation for P2P)")
    public ResponseEntity<MessageAndConversationResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(messageService.sendMessage(userId, request));
    }

    @PostMapping("/ai")
    @Operation(summary = "Send a message to Fruvia Chatbot and persist both user/assistant messages")
    public ResponseEntity<ApiResponse<AiChatResponse>> chatWithAi(
            @Valid @RequestBody ChatWithAiRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        AiChatResponse response = aiChatService.chatWithAi(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Gửi tin nhắn AI thành công"));
    }

    @GetMapping("/ai/conversation")
    @Operation(summary = "Get or create AI conversation for current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> ensureAiConversation() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        ConversationResponse response = aiChatService.ensureAiConversation(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy cuộc hội thoại AI thành công"));
    }

    @PostMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream AI response via Server-Sent Events")
    public ResponseEntity<SseEmitter> chatWithAiStream(
            @Valid @RequestBody ChatWithAiRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(aiChatService.chatWithAiStream(userId, request));
    }

    @GetMapping("/conversation/{conversationId}")
    @Operation(summary = "Get messages in a conversation (Pagination)")
    public ResponseEntity<Page<MessageResponse>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (beforeId != null && !beforeId.isEmpty()) {
            return ResponseEntity.ok(messageService.getMessagesBefore(conversationId, beforeId, size, userId));
        }

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.getConversationMessages(conversationId, pageable, userId));
    }

    @GetMapping("/conversation/{conversationId}/media")
    @Operation(summary = "Get all media messages (Images, Videos, Files) in a conversation")
    public ResponseEntity<List<MessageResponse>> getConversationMedia(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getConversationMedia(conversationId));
    }

    @GetMapping("/conversation/{conversationId}/around/{messageId}")
    @Operation(summary = "Get messages around a specific message (for jump-to-message feature)")
    public ResponseEntity<List<MessageResponse>> getMessagesAround(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "40") int size) {
        return ResponseEntity.ok(messageService.getMessagesAround(conversationId, messageId, size / 2));
    }

    @GetMapping("/conversation/{conversationId}/links")
    @Operation(summary = "Get all shared links in a conversation")
    public ResponseEntity<List<MessageResponse>> getConversationLinks(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getConversationLinks(conversationId));
    }

    @PutMapping("/{messageId}")
    @Operation(summary = "Edit a message (within 15 minutes)")
    public ResponseEntity<ApiResponse<MessageResponse>> updateMessage(
            @PathVariable String messageId,
            @RequestParam String content) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            MessageResponse response = messageService.updateMessage(messageId, content, userId);
            return ResponseEntity.ok(ApiResponse.success(response, "Chỉnh sửa tin nhắn thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("EDIT_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/{messageId}/recall")
    @Operation(summary = "Recall a message (within 60 minutes) - removes for everyone")
    public ResponseEntity<ApiResponse<MessageResponse>> recallMessage(
            @PathVariable String messageId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            MessageResponse response = messageService.recallMessage(messageId, userId);
            return ResponseEntity.ok(ApiResponse.success(response, "Thu hồi tin nhắn thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("RECALL_FAILED", e.getMessage()));
        }
    }

    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete a message (soft delete)")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        messageService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{messageId}/local")
    @Operation(summary = "Delete a message locally (only for the requesting user)")
    public ResponseEntity<ApiResponse<Void>> deleteMessageLocal(
            @PathVariable String messageId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        messageService.deleteMessageLocal(messageId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa tin nhắn thành công"));
    }

    @PostMapping("/{messageId}/react")
    @Operation(summary = "Add or remove a reaction to a message")
    public ResponseEntity<ApiResponse<Void>> reactToMessage(
            @PathVariable String messageId,
            @Valid @RequestBody ReactMessageRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        messageService.addReaction(messageId, userId, request.getReactionType());
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật cảm xúc thành công"));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "Get a pre-signed URL for uploading a file to S3")
    public ResponseEntity<ApiResponse<String>> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String fileType) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String url = s3Service.generatePresignedUrl(fileName, fileType);
        return ResponseEntity.ok(ApiResponse.success(url, "Lấy URL upload thành công"));
    }

    @DeleteMapping("/conversations/{conversationId}/all")
    @Operation(summary = "Clear all messages in a personal conversation (AI or My Documents)", description = "Hard-deletes every message, reaction, attachment and pinned entry in a "
            +
            "SELF-type conversation, and removes the corresponding S3 media objects in parallel. " +
            "Only the conversation owner may call this endpoint.")
    public ResponseEntity<ApiResponse<Void>> clearConversationAll(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        messageService.clearConversationAll(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa toàn bộ tin nhắn thành công"));
    }
}
