package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.response.message.PinnedMessageResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.message.PinnedMessageService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Pinned Message", description = "Pinned message management APIs")
public class PinnedMessageController {

    private final PinnedMessageService pinnedMessageService;

    @PostMapping("/{messageId}/pin")
    @Operation(summary = "Pin a message in a conversation")
    public ResponseEntity<ApiResponse<PinnedMessageResponse>> pinMessage(@PathVariable String messageId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            PinnedMessageResponse response = pinnedMessageService.pinMessage(messageId, userId);
            return ResponseEntity.ok(ApiResponse.success(response, "Ghim tin nhắn thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("PIN_FAILED", e.getMessage()));
        }
    }

    @DeleteMapping("/{messageId}/pin")
    @Operation(summary = "Unpin a message in a conversation")
    public ResponseEntity<ApiResponse<Void>> unpinMessage(@PathVariable String messageId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            pinnedMessageService.unpinMessage(messageId, userId);
            return ResponseEntity.ok(ApiResponse.success(null, "Bỏ ghim tin nhắn thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("UNPIN_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/conversations/{conversationId}/pinned")
    @Operation(summary = "Get all pinned messages in a conversation (includes content, messageType, mediaUrl)")
    public ResponseEntity<ApiResponse<List<PinnedMessageResponse>>> getPinnedMessages(
            @PathVariable String conversationId) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            List<PinnedMessageResponse> response = pinnedMessageService.getPinnedMessages(conversationId, userId);
            return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách tin nhắn ghim thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("GET_PINNED_FAILED", e.getMessage()));
        }
    }
}
