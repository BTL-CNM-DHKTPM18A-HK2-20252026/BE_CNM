package iuh.fit.service.message;

import iuh.fit.dto.response.message.PinnedMessageResponse;
import iuh.fit.entity.Message;
import iuh.fit.entity.PinnedMessage;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.PinnedMessageRepository;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinnedMessageService {

        private static final int MAX_PINNED_MESSAGES = 20;

        private final PinnedMessageRepository pinnedMessageRepository;
        private final MessageRepository messageRepository;
        private final ConversationMemberRepository conversationMemberRepository;
        private final UserDetailRepository userDetailRepository;
        private final SimpMessageSendingOperations messagingTemplate;

        @Transactional
        public PinnedMessageResponse pinMessage(String messageId, String userId) {
                Message message = messageRepository.findById(messageId)
                                .orElseThrow(() -> new RuntimeException("Message not found"));

                String conversationId = message.getConversationId();

                // Verify user is a member of the conversation
                conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));

                // Check if already pinned
                if (pinnedMessageRepository.findByMessageIdAndConversationId(messageId, conversationId).isPresent()) {
                        throw new RuntimeException("Message is already pinned");
                }

                // Check max pinned limit
                long count = pinnedMessageRepository.countByConversationId(conversationId);
                if (count >= MAX_PINNED_MESSAGES) {
                        throw new RuntimeException(
                                        "Maximum pinned messages limit reached (" + MAX_PINNED_MESSAGES + ")");
                }

                PinnedMessage pinned = PinnedMessage.builder()
                                .messageId(messageId)
                                .conversationId(conversationId)
                                .pinnedByUserId(userId)
                                .pinnedAt(LocalDateTime.now())
                                .build();

                pinned = pinnedMessageRepository.save(pinned);
                log.info("Message pinned: {} in conversation: {} by user: {}", messageId, conversationId, userId);

                PinnedMessageResponse response = buildResponse(pinned, message, userId);

                // Broadcast pin event via WebSocket
                Map<String, Object> pinEvent = new HashMap<>();
                pinEvent.put("type", "MESSAGE_PIN");
                pinEvent.put("messageId", messageId);
                pinEvent.put("conversationId", conversationId);
                pinEvent.put("pinnedBy", userId);
                pinEvent.put("pinnedByName", response.getPinnedByUserName());
                pinEvent.put("pinnedAt", pinned.getPinnedAt().toString());
                pinEvent.put("content", message.getContent());
                pinEvent.put("messageType", message.getMessageType());
                messagingTemplate.convertAndSend("/topic/chat/" + conversationId, pinEvent);

                return response;
        }

        @Transactional
        public void unpinMessage(String messageId, String userId) {
                Message message = messageRepository.findById(messageId)
                                .orElseThrow(() -> new RuntimeException("Message not found"));

                String conversationId = message.getConversationId();

                // Verify user is a member of the conversation
                conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));

                PinnedMessage pinned = pinnedMessageRepository
                                .findByMessageIdAndConversationId(messageId, conversationId)
                                .orElseThrow(() -> new RuntimeException("Message is not pinned"));

                pinnedMessageRepository.delete(pinned);
                log.info("Message unpinned: {} in conversation: {} by user: {}", messageId, conversationId, userId);

                // Broadcast unpin event via WebSocket
                Map<String, Object> unpinEvent = new HashMap<>();
                unpinEvent.put("type", "MESSAGE_UNPIN");
                unpinEvent.put("messageId", messageId);
                unpinEvent.put("conversationId", conversationId);
                unpinEvent.put("unpinnedBy", userId);
                messagingTemplate.convertAndSend("/topic/chat/" + conversationId, unpinEvent);
        }

        public List<PinnedMessageResponse> getPinnedMessages(String conversationId, String userId) {
                // Verify user is a member of the conversation
                conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));

                List<PinnedMessage> pinnedMessages = pinnedMessageRepository
                                .findByConversationIdOrderByPinnedAtDesc(conversationId);

                return pinnedMessages.stream()
                                .map(pin -> {
                                        Message msg = messageRepository.findById(pin.getMessageId()).orElse(null);
                                        if (msg == null)
                                                return null;
                                        return buildResponse(pin, msg, pin.getPinnedByUserId());
                                })
                                .filter(r -> r != null)
                                .collect(Collectors.toList());
        }

        private PinnedMessageResponse buildResponse(PinnedMessage pin, Message message, String pinnedByUserId) {
                UserDetail sender = userDetailRepository.findById(message.getSenderId()).orElse(null);
                UserDetail pinnedBy = userDetailRepository.findById(pinnedByUserId).orElse(null);

                return PinnedMessageResponse.builder()
                                .id(pin.getId())
                                .messageId(pin.getMessageId())
                                .conversationId(pin.getConversationId())
                                .senderId(message.getSenderId())
                                .senderName(sender != null ? sender.getDisplayName() : "Unknown")
                                .senderAvatarUrl(sender != null ? sender.getAvatarUrl() : null)
                                .content(message.getContent())
                                .messageType(message.getMessageType().name())
                                .pinnedAt(pin.getPinnedAt())
                                .messageCreatedAt(message.getCreatedAt())
                                .pinnedByUserId(pinnedByUserId)
                                .pinnedByUserName(pinnedBy != null ? pinnedBy.getDisplayName() : "Unknown")
                                .build();
        }
}
