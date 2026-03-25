package iuh.fit.service.message;

import iuh.fit.dto.request.message.SendMessageRequest;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Message;
import iuh.fit.entity.MessageAttachment;
import iuh.fit.entity.MessageReaction;
import iuh.fit.enums.MessageType;
import iuh.fit.enums.ReactionType;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.MessageReactionRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.entity.UserDetail;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.MemberRole;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.utils.LinkScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.messaging.simp.SimpMessageSendingOperations;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageMapper messageMapper;
    private final SimpMessageSendingOperations messagingTemplate;
    private final MessageReactionRepository messageReactionRepository;
    private final UserDetailRepository userDetailRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationMapper conversationMapper;
    private final LinkScraper linkScraper;

    @Transactional
    public MessageAndConversationResponse sendMessage(String senderId, SendMessageRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        MessageType type = MessageType.TEXT;

        // Use explicitly provided messageType first (e.g., IMAGE, VIDEO, MEDIA, VOICE)
        if (request.getMessageType() != null) {
            try {
                type = MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                type = MessageType.TEXT;
            }
        }

        // Auto-detect LINK type only if no explicit type was provided (i.e., still
        // TEXT)
        if (type == MessageType.TEXT) {
            String urlPattern = "^https?://[\\w\\d.-]+(:\\d+)?(/[\\w\\d./?%&=-]*)?$";
            if (content.matches(urlPattern)) {
                type = MessageType.LINK;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String convId = request.getConversationId();
        Conversations conv;

        // Lazy Creation logic for P2P
        if (convId == null || convId.isEmpty()) {
            if (request.getRecipientId() == null) {
                throw new RuntimeException("RecipientId or ConversationId is required");
            }
            List<String> sortedParticipants = Stream.of(senderId, request.getRecipientId())
                    .sorted()
                    .collect(Collectors.toList());

            conv = conversationRepository.findPrivateConversation(sortedParticipants)
                    .orElseGet(() -> {
                        Conversations newConv = Conversations.builder()
                                .conversationId(UUID.randomUUID().toString())
                                .conversationType(ConversationType.PRIVATE)
                                .participants(sortedParticipants)
                                .createdAt(now)
                                .updatedAt(now)
                                .isDeleted(false)
                                .build();

                        Conversations saved = conversationRepository.save(newConv);

                        sortedParticipants.forEach(uid -> {
                            conversationMemberRepository.save(ConversationMember.builder()
                                    .conversationId(saved.getConversationId())
                                    .userId(uid)
                                    .role(MemberRole.MEMBER)
                                    .joinedAt(now)
                                    .build());
                        });
                        return saved;
                    });
            convId = conv.getConversationId();
        } else {
            conv = conversationRepository.findById(convId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));

            // Validate sender is a member of this conversation
            conversationMemberRepository.findByConversationIdAndUserId(convId, senderId)
                    .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của cuộc hội thoại này"));
        }

        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(convId)
                .senderId(senderId)
                .content(content)
                .messageType(type)
                .replyToMessageId(request.getReplyToMessageId())
                .isEdited(false)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .voiceDuration(request.getVoiceDuration())
                .build();

        // If it's a LINK, scrape metadata
        if (type == MessageType.LINK) {
            try {
                LinkScraper.LinkMetadata metadata = linkScraper.scrape(content);
                message.setLinkTitle(metadata.getTitle());
                message.setLinkThumbnail(metadata.getThumbnail());
            } catch (Exception e) {
                log.warn("Error scraping metadata during message send: {}", e.getMessage());
            }
        }

        message = messageRepository.save(message);
        log.info("Message sent: {} in conversation: {}", message.getMessageId(), convId);

        // Update conversation last message denormalized fields
        String snippet = message.getContent();
        if (message.getMessageType() == MessageType.IMAGE)
            snippet = "[Hình ảnh]";
        else if (message.getMessageType() == MessageType.VIDEO)
            snippet = "[Video]";
        else if (message.getMessageType() == MessageType.MEDIA)
            snippet = "[File]";

        conv.setLastMessageId(message.getMessageId());
        conv.setLastMessageContent(snippet);
        conv.setLastMessageTime(message.getCreatedAt());
        conv.setUpdatedAt(message.getCreatedAt());
        conversationRepository.save(conv);

        MessageResponse msgResponse = messageMapper.toResponse(message);
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(convId);
        ConversationResponse convResponse = conversationMapper.toResponse(conv, members);

        MessageAndConversationResponse finalResponse = MessageAndConversationResponse.builder()
                .message(msgResponse)
                .conversation(convResponse)
                .build();

        // Broadcast to specific conversation topic
        messagingTemplate.convertAndSend("/topic/chat/" + convId, finalResponse);

        // Notify all members (works for both PRIVATE and GROUP)
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(senderId)) {
                messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/notifications", finalResponse);
            }
        }

        return finalResponse;
    }

    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(messageMapper::toResponse);
    }

    public Page<MessageResponse> getMessagesBefore(String conversationId, String beforeId, int size) {
        Pageable pageable = PageRequest.of(0, size);

        // Find the cursor message first
        Message beforeMsg = messageRepository.findById(beforeId).orElse(null);
        if (beforeMsg == null) {
            return Page.empty();
        }

        return messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                conversationId, beforeMsg.getCreatedAt(), pageable)
                .map(messageMapper::toResponse);
    }

    public List<MessageResponse> getConversationMedia(String conversationId) {
        List<String> mediaTypes = Arrays.asList(
                MessageType.IMAGE.name(),
                MessageType.VIDEO.name(),
                MessageType.MEDIA.name());
        return messageRepository.findByConversationIdAndMessageTypeInOrderByCreatedAtDesc(conversationId, mediaTypes)
                .stream()
                .map(messageMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<MessageResponse> getConversationLinks(String conversationId) {
        return messageRepository
                .findByConversationIdAndMessageTypeInOrderByCreatedAtDesc(conversationId,
                        List.of(MessageType.LINK.name()))
                .stream()
                .map(messageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse updateMessage(String messageId, String content, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Not authorized to edit this message");
        }

        message.setContent(content);
        message.setIsEdited(true);
        message.setUpdatedAt(LocalDateTime.now());

        message = messageRepository.save(message);
        log.info("Message updated: {}", messageId);

        return messageMapper.toResponse(message);
    }

    @Transactional
    public void deleteMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Not authorized to delete this message");
        }

        message.setIsDeleted(true);
        messageRepository.save(message);
        log.info("Message deleted: {}", messageId);
    }

    @Transactional
    public void addReaction(String messageId, String userId, ReactionType reactionType) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // New reaction (can have multiple reaction types per user)
        // Build new reaction directly with explicit random UUID
        MessageReaction newReaction = MessageReaction.builder()
                .id(UUID.randomUUID().toString())
                .messageId(messageId)
                .userId(userId)
                .icon(reactionType)
                .build();
        messageReactionRepository.insert(newReaction); // Force insert instead of save
        log.info("Added reaction {} (ID: {}) to message: {} by user: {}", reactionType, newReaction.getId(), messageId,
                userId);

        // Broadcast reaction event via websocket
        UserDetail reactor = userDetailRepository.findById(userId).orElse(null);
        if (reactor == null) {
            log.warn("USER NOT FOUND in user_detail for ID: {}", userId);
        } else {
            log.info("Found reactor: {} (ID: {})", reactor.getDisplayName(), reactor.getUserId());
        }

        Map<String, Object> reactionEvent = new HashMap<>();
        reactionEvent.put("type", "REACTION_UPDATE");
        reactionEvent.put("messageId", messageId);
        reactionEvent.put("userId", userId);
        reactionEvent.put("userName", reactor != null ? reactor.getDisplayName() : "Unknown");
        reactionEvent.put("userAvatar", reactor != null ? reactor.getAvatarUrl() : null);
        reactionEvent.put("reactionId", newReaction.getId());
        reactionEvent.put("action", "ADD");
        reactionEvent.put("reactionType", reactionType);

        messagingTemplate.convertAndSend("/topic/chat/" + message.getConversationId(), reactionEvent);
    }
}
