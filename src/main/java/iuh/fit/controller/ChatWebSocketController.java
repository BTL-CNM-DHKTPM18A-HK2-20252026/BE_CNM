package iuh.fit.controller;

import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.UserSetting;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.service.message.TypingIndicatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket controller for real-time chat features.
 * Handles typing indicators and read receipts via STOMP.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserSettingRepository userSettingRepository;
    private final TypingIndicatorService typingIndicatorService;


    /**
     * Typing indicator: Client sends to /app/chat/{conversationId}/typing
     * Server broadcasts to /topic/chat/{conversationId}/typing
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void handleTyping(
            @DestinationVariable String conversationId,
            @Payload Map<String, Object> payload) {
        // Record typing state in Redis with auto-expiry (3s TTL)
        String userId = (String) payload.get("userId");
        if (userId != null) {
            typingIndicatorService.setTyping(conversationId, userId);
        }

        // Broadcast to subscribers for real-time update
        messagingTemplate.convertAndSend(
                "/topic/chat/" + conversationId + "/typing", payload);
    }

    /**
     * Read receipt: Client sends to /app/chat/{conversationId}/read
     * Server persists lastReadMessageId and broadcasts to
     * /topic/chat/{conversationId}/read
     *
     * Payload: { "userId": "...", "displayName": "...", "messageId": "...",
     * "avatarUrl": "..." }
     */
    @MessageMapping("/chat/{conversationId}/read")
    public void handleReadReceipt(
            @DestinationVariable String conversationId,
            @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String messageId = (String) payload.get("messageId");

        // Persist last read message
        if (userId != null && messageId != null) {
            conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                    .ifPresent(member -> {
                        member.setLastReadMessageId(messageId);
                        member.setLastReadAt(LocalDateTime.now());
                        conversationMemberRepository.save(member);
                    });
        }

        // Only broadcast read receipt if user has not disabled it
        boolean readReceiptsHidden = userSettingRepository.findById(userId)
                .map(s -> Boolean.FALSE.equals(s.getShowReadReceipts()))
                .orElse(false);

        if (!readReceiptsHidden) {
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversationId + "/read", payload);
        }
    }
}
