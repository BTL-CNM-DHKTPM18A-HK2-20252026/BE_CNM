package iuh.fit.controller;

import iuh.fit.entity.ConversationMember;
import iuh.fit.repository.ConversationMemberRepository;
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

    /**
     * Typing indicator: Client sends to /app/chat/{conversationId}/typing
     * Server broadcasts to /topic/chat/{conversationId}/typing
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void handleTyping(
            @DestinationVariable String conversationId,
            @Payload Map<String, Object> payload) {
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

        // Broadcast read receipt to all members
        messagingTemplate.convertAndSend(
                "/topic/chat/" + conversationId + "/read", payload);
    }
}
