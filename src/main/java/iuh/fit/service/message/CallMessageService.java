package iuh.fit.service.message;

import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Message;
import iuh.fit.enums.MessageType;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saves a call-history system message to MongoDB and broadcasts it via
 * WebSocket.
 *
 * <p>
 * Message types handled:
 * <ul>
 * <li>{@link MessageType#CALL_MISSED} — content = "" (caller cancelled before
 * answer)</li>
 * <li>{@link MessageType#CALL_REJECTED} — content = "" (callee rejected)</li>
 * <li>{@link MessageType#CALL_ENDED} — content = duration in seconds as
 * string</li>
 * </ul>
 *
 * <p>
 * The {@code senderId} is always the caller's userId so the frontend can
 * determine
 * perspective ("Bạn đã gọi nhỡ" vs "Cuộc gọi nhỡ từ …").
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallMessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Persists call-history message and pushes it to all conversation subscribers.
     *
     * @param conversationId  the conversation the call occurred in
     * @param callerId        userId of the person who initiated the call
     * @param type            CALL_MISSED, CALL_REJECTED, or CALL_ENDED
     * @param durationSeconds call duration (only meaningful for CALL_ENDED, ignored
     *                        otherwise)
     */
    public void saveAndBroadcast(String conversationId, String callerId, MessageType type, Integer durationSeconds) {
        if (conversationId == null || conversationId.isBlank()) {
            log.warn("[CallMessage] No conversationId — skipping call history message (type={})", type);
            return;
        }

        Optional<Conversations> convOpt = conversationRepository.findById(conversationId);
        if (convOpt.isEmpty()) {
            log.warn("[CallMessage] Conversation {} not found — skipping", conversationId);
            return;
        }

        String content = buildContent(type, durationSeconds);
        LocalDateTime now = LocalDateTime.now();

        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(callerId)
                .content(content)
                .messageType(type)
                .isEdited(false)
                .isDeleted(false)
                .isRecalled(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        message = messageRepository.save(message);
        log.info("[CallMessage] Saved {} message {} in conversation {}", type, message.getMessageId(), conversationId);

        // Update conversation snippet
        Conversations conv = convOpt.get();
        conv.setLastMessageId(message.getMessageId());
        conv.setLastMessageContent(buildSnippet(type));
        conv.setLastMessageTime(now);
        conv.setUpdatedAt(now);
        conversationRepository.save(conv);

        // Build and broadcast response
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        MessageResponse msgResponse = messageMapper.toResponse(message);
        ConversationResponse convResponse = conversationMapper.toResponse(conv, members);

        MessageAndConversationResponse payload = MessageAndConversationResponse.builder()
                .message(msgResponse)
                .conversation(convResponse)
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, payload);
        log.info("[CallMessage] Broadcast {} to /topic/chat/{}", type, conversationId);
    }

    private String buildContent(MessageType type, Integer durationSeconds) {
        if (type == MessageType.CALL_ENDED && durationSeconds != null) {
            return String.valueOf(durationSeconds);
        }
        return "";
    }

    /** Short snippet shown in conversation preview list. */
    private String buildSnippet(MessageType type) {
        return switch (type) {
            case CALL_MISSED -> "📵 Cuộc gọi nhỡ";
            case CALL_REJECTED -> "🚫 Cuộc gọi bị từ chối";
            case CALL_ENDED -> "📞 Cuộc gọi đã kết thúc";
            default -> "";
        };
    }
}
