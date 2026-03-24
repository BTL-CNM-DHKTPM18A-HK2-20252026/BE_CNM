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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import iuh.fit.utils.JwtUtils;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.message.SendMessageRequest;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.service.message.MessageService;
import iuh.fit.service.s3.S3Service;
import iuh.fit.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Message", description = "Message management APIs")
public class MessageController {
    
    private final MessageService messageService;
    private final S3Service s3Service;
    
    @PostMapping
    @Operation(summary = "Send a message")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(messageService.sendMessage(userId, request));
    }
    
    @GetMapping("/conversation/{conversationId}")
    @Operation(summary = "Get messages in a conversation (Pagination)")
    public ResponseEntity<Page<MessageResponse>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (beforeId != null && !beforeId.isEmpty()) {
            return ResponseEntity.ok(messageService.getMessagesBefore(conversationId, beforeId, size));
        }

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.getConversationMessages(conversationId, pageable));
    }
    
    @PutMapping("/{messageId}")
    @Operation(summary = "Update a message")
    public ResponseEntity<MessageResponse> updateMessage(
            @PathVariable String messageId,
            @RequestParam String content) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(messageService.updateMessage(messageId, content, userId));
    }
    
    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete a message")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        messageService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "Get a pre-signed URL for uploading a file to S3")
    public ResponseEntity<ApiResponse<String>> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String fileType) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String url = s3Service.generatePresignedUrl(fileName, fileType);
        return ResponseEntity.ok(ApiResponse.success(url, "Lấy URL upload thành công"));
    }
}
