package iuh.fit.service.message;

import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.dto.response.message.PinnedMessageResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Message;
import iuh.fit.entity.PinnedMessage;
import iuh.fit.entity.UserDetail;
import iuh.fit.enums.AiRole;
import iuh.fit.enums.MessageType;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.PinnedMessageRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinnedMessageService {

        private static final int MAX_PINNED_MESSAGES = 20;

        private final PinnedMessageRepository pinnedMessageRepository;
        private final MessageRepository messageRepository;
        private final ConversationRepository conversationRepository;
        private final ConversationMemberRepository conversationMemberRepository;
        private final UserDetailRepository userDetailRepository;
        private final MessageMapper messageMapper;
        private final ConversationMapper conversationMapper;
        private final MessageCacheService messageCacheService;
        private final MessageProducerService messageProducerService;
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
                                .content(message.getContent())
                                .messageType(message.getMessageType() != null ? message.getMessageType().name() : null)
                                .mediaUrl(resolvePinnedMediaUrl(message))
                                .pinnedByUserId(userId)
                                .pinnedAt(LocalDateTime.now())
                                .build();

                pinned = pinnedMessageRepository.save(pinned);
                log.info("Message pinned: {} in conversation: {} by user: {}", messageId, conversationId, userId);

                PinnedMessageResponse response = buildResponse(pinned, message, userId);
                LocalDateTime now = LocalDateTime.now();
                Message systemMessage = createSystemMessage(
                        conversationId,
                        userId,
                        buildPinSystemContent(userId, true),
                        now);

                Message savedSystemMessage = messageRepository.save(systemMessage);
                syncConversationLastMessage(message.getConversationId(), savedSystemMessage);

                MessageResponse systemResponse = messageMapper.toResponse(savedSystemMessage);
                ConversationResponse conversationResponse = buildConversationResponse(conversationId);
                MessageAndConversationResponse chatPayload = MessageAndConversationResponse.builder()
                                .message(systemResponse)
                                .conversation(conversationResponse)
                                .build();

                // Broadcast pin event via WebSocket
                Map<String, Object> pinEvent = new HashMap<>();
                pinEvent.put("type", "MESSAGE_PIN");
                pinEvent.put("messageId", messageId);
                pinEvent.put("replyToMessageId", messageId);
                pinEvent.put("conversationId", conversationId);
                pinEvent.put("pinnedBy", userId);
                pinEvent.put("pinnedByName", response.getPinnedByUserName());
                pinEvent.put("pinnedAt", pinned.getPinnedAt().toString());
                pinEvent.put("content", message.getContent());
                pinEvent.put("messageType", message.getMessageType());
                afterCommit(() -> {
                        messageCacheService.pushMessage(savedSystemMessage);
                        messageProducerService.send(savedSystemMessage);
                        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, chatPayload);
                        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, pinEvent);
                });

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

                LocalDateTime now = LocalDateTime.now();
                Message systemMessage = createSystemMessage(
                        conversationId,
                        userId,
                        buildPinSystemContent(userId, false),
                        now);

                Message savedSystemMessage = messageRepository.save(systemMessage);
                syncConversationLastMessage(conversationId, savedSystemMessage);

                MessageResponse systemResponse = messageMapper.toResponse(savedSystemMessage);
                ConversationResponse conversationResponse = buildConversationResponse(conversationId);
                MessageAndConversationResponse chatPayload = MessageAndConversationResponse.builder()
                                .message(systemResponse)
                                .conversation(conversationResponse)
                                .build();

                // Broadcast unpin event via WebSocket
                Map<String, Object> unpinEvent = new HashMap<>();
                unpinEvent.put("type", "MESSAGE_UNPIN");
                unpinEvent.put("messageId", messageId);
                unpinEvent.put("replyToMessageId", messageId);
                unpinEvent.put("conversationId", conversationId);
                unpinEvent.put("unpinnedBy", userId);
                unpinEvent.put("unpinnedByName", userDetailRepository.findByUserId(userId)
                                .map(UserDetail::getDisplayName)
                                .orElse("Một thành viên"));
                unpinEvent.put("content", pinned.getContent() != null ? pinned.getContent() : message.getContent());
                unpinEvent.put("messageType", message.getMessageType());
                afterCommit(() -> {
                        messageCacheService.pushMessage(savedSystemMessage);
                        messageProducerService.send(savedSystemMessage);
                        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, chatPayload);
                        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, unpinEvent);
                });
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
                                        return buildResponse(pin, msg, pin.getPinnedByUserId());
                                })
                                .collect(Collectors.toList());
        }

        private PinnedMessageResponse buildResponse(PinnedMessage pin, Message message, String pinnedByUserId) {
                String senderId = message != null ? message.getSenderId() : null;
                UserDetail sender = senderId != null ? userDetailRepository.findById(senderId).orElse(null) : null;
                UserDetail pinnedBy = userDetailRepository.findById(pinnedByUserId).orElse(null);

                String resolvedContent = pin.getContent();
                if ((resolvedContent == null || resolvedContent.isBlank()) && message != null) {
                        resolvedContent = message.getContent();
                }

                String resolvedMessageType = pin.getMessageType();
                if ((resolvedMessageType == null || resolvedMessageType.isBlank())
                                && message != null && message.getMessageType() != null) {
                        resolvedMessageType = message.getMessageType().name();
                }

                String resolvedMediaUrl = pin.getMediaUrl();
                if ((resolvedMediaUrl == null || resolvedMediaUrl.isBlank()) && message != null) {
                        resolvedMediaUrl = resolvePinnedMediaUrl(message);
                }

                return PinnedMessageResponse.builder()
                                .id(pin.getId())
                                .messageId(pin.getMessageId())
                                .conversationId(pin.getConversationId())
                                .senderId(senderId)
                                .senderName(sender != null ? sender.getDisplayName() : "Unknown")
                                .senderAvatarUrl(sender != null ? sender.getAvatarUrl() : null)
                                .content(resolvedContent)
                                .messageType(resolvedMessageType)
                                .mediaUrl(resolvedMediaUrl)
                                .pinnedAt(pin.getPinnedAt())
                                .messageCreatedAt(message != null ? message.getCreatedAt() : null)
                                .pinnedByUserId(pinnedByUserId)
                                .pinnedByUserName(pinnedBy != null ? pinnedBy.getDisplayName() : "Unknown")
                                .build();
        }

        private String resolvePinnedMediaUrl(Message message) {
                if (message == null || message.getMessageType() == null) {
                        return null;
                }

                if (message.getMessageType() == MessageType.IMAGE
                                || message.getMessageType() == MessageType.VIDEO) {
                        return message.getContent();
                }

                return null;
        }

        private Message createSystemMessage(String conversationId, String actorId, String content, LocalDateTime now) {
                return Message.builder()
                                .messageId(UUID.randomUUID().toString())
                                .conversationId(conversationId)
                                .senderId(actorId)
                                .role(AiRole.SYSTEM)
                                .messageType(MessageType.SYSTEM)
                                .content(content)
                                .createdAt(now)
                                .updatedAt(now)
                                .isDeleted(false)
                                .isRecalled(false)
                                .isEdited(false)
                                .aiGenerated(false)
                                .build();
        }

        private String buildPinSystemContent(String actorId, boolean isPin) {
                String actorName = userDetailRepository.findByUserId(actorId)
                                .map(UserDetail::getDisplayName)
                                .orElse("Một thành viên");
                return isPin
                                ? actorName + " đã ghim một tin nhắn"
                                : actorName + " đã bỏ ghim một tin nhắn";
        }

        private void syncConversationLastMessage(String conversationId, Message systemMessage) {
                Conversations conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new RuntimeException("Conversation not found"));

                conversation.setLastMessageId(systemMessage.getMessageId());
                conversation.setLastMessageContent(systemMessage.getContent());
                conversation.setLastMessageTime(systemMessage.getCreatedAt());
                conversation.setUpdatedAt(systemMessage.getCreatedAt());
                conversationRepository.save(conversation);
        }

        private ConversationResponse buildConversationResponse(String conversationId) {
                Conversations conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new RuntimeException("Conversation not found"));
                List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
                return conversationMapper.toResponse(conversation, members);
        }

        private void afterCommit(Runnable action) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                        action.run();
                                }
                        });
                        return;
                }

                action.run();
        }
}
