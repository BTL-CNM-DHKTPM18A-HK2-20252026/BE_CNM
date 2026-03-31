package iuh.fit.service.message;

import iuh.fit.dto.request.message.SendMessageRequest;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.Message;
import iuh.fit.entity.MessageAttachment;
import iuh.fit.entity.MessageReaction;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.ConversationStatus;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.enums.MessageType;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.enums.ReactionType;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.MessageReactionRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.entity.UserDetail;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.MemberRole;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.utils.LinkScraper;
import iuh.fit.service.storage.StorageService;
import iuh.fit.service.search.SearchService;
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
    private final StorageService storageService;
    private final FriendshipRepository friendshipRepository;
    private final UserSettingRepository userSettingRepository;
    private final SearchService searchService;

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

        // ── Stranger-messaging guard (PRIVATE only) ──────────────────────────────
        if (conv.getConversationType() == ConversationType.PRIVATE) {
            // Find the other participant
            String recipientId = conv.getParticipants() == null ? null
                    : conv.getParticipants().stream().filter(id -> !id.equals(senderId)).findFirst().orElse(null);

            if (recipientId != null) {
                // Check if receiver blocked sender
                boolean blocked = friendshipRepository
                        .findByRequesterIdAndReceiverIdAndStatus(recipientId, senderId, FriendshipStatus.BLOCKED)
                        .isPresent();
                if (blocked) {
                    throw new RuntimeException("Bạn đã bị chặn bởi người dùng này");
                }

                // Check receiver's privacy setting
                UserSetting receiverSetting = userSettingRepository.findById(recipientId).orElse(null);
                if (receiverSetting != null
                        && receiverSetting.getWhoCanSendMessages() == PrivacyLevel.FRIEND_ONLY) {
                    // Only friends may send messages — check friendship
                    boolean areFriends = friendshipRepository
                            .findByRequesterIdAndReceiverId(senderId, recipientId)
                            .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                            .isPresent();
                    if (!areFriends) {
                        throw new RuntimeException("Người dùng này chỉ nhận tin nhắn từ bạn bè");
                    }
                }

                // If conv is still NORMAL and they're not friends → mark PENDING
                if (conv.getConversationStatus() == ConversationStatus.NORMAL
                        || conv.getConversationStatus() == null) {
                    boolean areFriends = friendshipRepository
                            .findByRequesterIdAndReceiverId(senderId, recipientId)
                            .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                            .isPresent();
                    if (!areFriends) {
                        conv.setConversationStatus(ConversationStatus.PENDING);
                    }
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

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

        // Index message in Elasticsearch
        String senderDisplayName = userDetailRepository.findByUserId(senderId)
                .map(UserDetail::getDisplayName).orElse("Unknown");
        searchService.indexMessage(message, senderDisplayName);

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

        // Notify all members — for PENDING conversations send masked notification to
        // receiver
        boolean isPendingConv = conv.getConversationStatus() == ConversationStatus.PENDING;
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(senderId)) {
                if (isPendingConv) {
                    // Privacy: don't reveal message content, only notify about pending request
                    java.util.Map<String, Object> maskedNotification = new java.util.HashMap<>();
                    maskedNotification.put("type", "MESSAGE_REQUEST");
                    maskedNotification.put("conversationId", convId);
                    maskedNotification.put("senderName", message.getSenderId()); // resolved on FE
                    maskedNotification.put("text", "Bạn có một tin nhắn chờ mới");
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/notifications",
                            maskedNotification);
                } else {
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/notifications", finalResponse);
                }
            }
        }

        // Push storage update if this is a SELF (My Cloud) conversation and file was
        // sent
        if (conv.getConversationType() == ConversationType.SELF && type != MessageType.TEXT
                && type != MessageType.LINK) {
            storageService.pushStorageUpdate(senderId);
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

    // ── Time limits (minutes) ──
    private static final int EDIT_TIME_LIMIT_MINUTES = 15;
    private static final int RECALL_TIME_LIMIT_MINUTES = 60;

    @Transactional
    public MessageResponse updateMessage(String messageId, String content, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Not authorized to edit this message");
        }

        if (Boolean.TRUE.equals(message.getIsRecalled())) {
            throw new RuntimeException("Cannot edit a recalled message");
        }

        // Time limit check
        if (message.getCreatedAt() != null
                && message.getCreatedAt().plusMinutes(EDIT_TIME_LIMIT_MINUTES).isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Edit time limit exceeded (" + EDIT_TIME_LIMIT_MINUTES + " minutes)");
        }

        // Save edit history
        Message.EditHistory history = Message.EditHistory.builder()
                .previousContent(message.getContent())
                .editedAt(LocalDateTime.now())
                .build();
        if (message.getEditHistory() == null) {
            message.setEditHistory(new java.util.ArrayList<>());
        }
        message.getEditHistory().add(history);

        message.setContent(content);
        message.setIsEdited(true);
        message.setUpdatedAt(LocalDateTime.now());

        message = messageRepository.save(message);
        log.info("Message edited: {}", messageId);

        MessageResponse response = messageMapper.toResponse(message);

        // Broadcast edit event via WebSocket
        Map<String, Object> editEvent = new HashMap<>();
        editEvent.put("type", "MESSAGE_EDIT");
        editEvent.put("messageId", messageId);
        editEvent.put("conversationId", message.getConversationId());
        editEvent.put("content", content);
        editEvent.put("isEdited", true);
        editEvent.put("updatedAt", message.getUpdatedAt().toString());
        messagingTemplate.convertAndSend("/topic/chat/" + message.getConversationId(), editEvent);

        return response;
    }

    @Transactional
    public MessageResponse recallMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Not authorized to recall this message");
        }

        if (Boolean.TRUE.equals(message.getIsRecalled())) {
            throw new RuntimeException("Message is already recalled");
        }

        // Time limit check
        if (message.getCreatedAt() != null
                && message.getCreatedAt().plusMinutes(RECALL_TIME_LIMIT_MINUTES).isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Recall time limit exceeded (" + RECALL_TIME_LIMIT_MINUTES + " minutes)");
        }

        message.setIsRecalled(true);
        message.setUpdatedAt(LocalDateTime.now());
        message = messageRepository.save(message);
        log.info("Message recalled: {}", messageId);

        MessageResponse response = messageMapper.toResponse(message);

        // Broadcast recall event via WebSocket
        Map<String, Object> recallEvent = new HashMap<>();
        recallEvent.put("type", "MESSAGE_RECALL");
        recallEvent.put("messageId", messageId);
        recallEvent.put("conversationId", message.getConversationId());
        recallEvent.put("senderId", userId);
        messagingTemplate.convertAndSend("/topic/chat/" + message.getConversationId(), recallEvent);

        return response;
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

    /**
     * Delete message locally (only for the requesting user).
     * The message remains visible to other participants.
     */
    @Transactional
    public void deleteMessageLocal(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (message.getLocalDeletedBy() == null) {
            message.setLocalDeletedBy(new java.util.ArrayList<>());
        }
        if (!message.getLocalDeletedBy().contains(userId)) {
            message.getLocalDeletedBy().add(userId);
        }
        messageRepository.save(message);
        log.info("Message locally deleted for user {}: {}", userId, messageId);
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
