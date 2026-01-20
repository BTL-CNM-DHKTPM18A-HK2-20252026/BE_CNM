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
import iuh.fit.dto.request.SendMessageRequest;
import iuh.fit.dto.response.MessageResponse;
import iuh.fit.service.message.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Message", description = "Message management APIs")
public class MessageController {
    
    private final MessageService messageService;
    
    @PostMapping
    @Operation(summary = "Send a message")
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.sendMessage(userId, request));
    }
    
    @GetMapping("/conversation/{conversationId}")
    @Operation(summary = "Get messages in a conversation")
    public ResponseEntity<Page<MessageResponse>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.getConversationMessages(conversationId, pageable));
    }
    
    @PutMapping("/{messageId}")
    @Operation(summary = "Update a message")
    public ResponseEntity<MessageResponse> updateMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String content) {
        return ResponseEntity.ok(messageService.updateMessage(messageId, content, userId));
    }
    
    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete a message")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId) {
        messageService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }
}
