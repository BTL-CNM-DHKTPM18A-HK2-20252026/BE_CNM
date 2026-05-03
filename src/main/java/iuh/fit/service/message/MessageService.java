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
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.enums.MessageType;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.enums.ReactionType;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.ForbiddenException;
import iuh.fit.exception.ResourceNotFoundException;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.MessageReactionRepository;
import iuh.fit.repository.PinnedMessageRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.repository.ConversationPermissionRepository;
import iuh.fit.entity.UserDetail;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.enums.MemberRole;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.utils.LinkScraper;
import iuh.fit.service.storage.StorageService;
import iuh.fit.service.search.SearchService;
import iuh.fit.dto.response.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private static final List<String> SSRF_BLOCKED_HOSTS = List.of(
            "169.254.169.254", "localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    private boolean isSsrfSafe(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            return host != null && SSRF_BLOCKED_HOSTS.stream()
                    .noneMatch(blocked -> host.equalsIgnoreCase(blocked))
                    && !host.startsWith("10.")
                    && !host.startsWith("192.168.")
                    && !host.startsWith("172.");
        } catch (Exception e) {
            return false;
        }
    }

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
    private final PinnedMessageRepository pinnedMessageRepository;
    private final ConversationPermissionRepository conversationPermissionRepository;
    private final MessageBucketService messageBucketService;
    private final MessageCacheService messageCacheService;
    private final MessageProducerService messageProducerService;

    @Transactional
    public MessageAndConversationResponse sendMessage(String senderId, SendMessageRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        MessageType type = resolveMessageType(request, content);
        LocalDateTime now = LocalDateTime.now();

        Conversations conv = resolveConversation(request, senderId, now);
        String convId = conv.getConversationId();

        validatePrivacyAndPermissions(conv, senderId, convId, now);

        Message message = buildMessage(request, senderId, convId, content, type, now);
        persistMessageToCache(message, type, content, request);

        ConversationStatus prevStatus = conv.getConversationStatus();
        updateConversationLastMessage(conv, message, senderId);

        MessageResponse msgResponse = messageMapper.toResponse(message);
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(convId);
        autoUnhideMembers(members, convId);

        ConversationResponse convResponse = conversationMapper.toResponse(conv, members);
        MessageAndConversationResponse finalResponse = MessageAndConversationResponse.builder()
                .message(msgResponse)
                .conversation(convResponse)
                .build();

        broadcastMessage(finalResponse, msgResponse, convResponse, convId, senderId, members, conv, content);

        if (conv.getConversationType() == ConversationType.SELF && type != MessageType.TEXT
                && type != MessageType.LINK) {
            storageService.pushStorageUpdate(senderId);
        }

        return finalResponse;
    }

    private MessageType resolveMessageType(SendMessageRequest request, String content) {
        MessageType type = MessageType.TEXT;
        if (request.getMessageType() != null) {
            try {
                type = MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                type = MessageType.TEXT;
            }
        }
        if (type == MessageType.TEXT) {
            String urlPattern = "^https?://[\\w\\d.-]+(:\\d+)?(/[\\w\\d./?%&=-]*)?$";
            if (content.matches(urlPattern)) {
                type = MessageType.LINK;
            }
        }
        return type;
    }

    private Conversations resolveConversation(SendMessageRequest request, String senderId, LocalDateTime now) {
        String convId = request.getConversationId();
        if (convId == null || convId.isEmpty()) {
            if (request.getRecipientId() == null) {
                throw new AppException(ErrorCode.RECIPIENT_REQUIRED);
            }
            List<String> sortedParticipants = Stream.of(senderId, request.getRecipientId())
                    .sorted()
                    .collect(Collectors.toList());
            return conversationRepository.findPrivateConversation(sortedParticipants)
                    .orElseGet(() -> {
                        Conversations newConv = Conversations.builder()
                                .conversationId(UUID.randomUUID().toString())
                                .conversationType(ConversationType.PRIVATE)
                                .participants(sortedParticipants)
                                .createdAt(now)
                                .updatedAt(now)
                                .isDeleted(false)
                                .build();
                        Conversations saved;
                        try {
                            saved = conversationRepository.save(newConv);
                        } catch (DuplicateKeyException e) {
                            return conversationRepository.findPrivateConversation(sortedParticipants)
                                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));
                        }
                        sortedParticipants.forEach(uid -> conversationMemberRepository.save(
                                ConversationMember.builder()
                                        .conversationId(saved.getConversationId())
                                        .userId(uid)
                                        .role(MemberRole.MEMBER)
                                        .joinedAt(now)
                                        .build()));
                        return saved;
                    });
        } else {
            Conversations conv = conversationRepository.findById(convId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));
            conversationMemberRepository.findByConversationIdAndUserId(convId, senderId)
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
            return conv;
        }
    }

    private void validatePrivacyAndPermissions(Conversations conv, String senderId, String convId, LocalDateTime now) {
        if (conv.getConversationType() == ConversationType.PRIVATE) {
            String recipientId = conv.getParticipants() == null ? null
                    : conv.getParticipants().stream().filter(id -> !id.equals(senderId)).findFirst().orElse(null);
            if (recipientId != null) {
                UserSetting receiverSetting = userSettingRepository.findById(recipientId).orElse(null);
                if (receiverSetting != null && Boolean.TRUE.equals(receiverSetting.getAccountLocked())) {
                    throw new ForbiddenException(ErrorCode.USER_ACCOUNT_LOCKED);
                }
                boolean blocked = friendshipRepository
                        .findByRequesterIdAndReceiverIdAndStatus(recipientId, senderId, FriendshipStatus.BLOCKED)
                        .isPresent();
                if (blocked) {
                    throw new ForbiddenException(ErrorCode.USER_BLOCKED_YOU);
                }
                if (receiverSetting != null && receiverSetting.getWhoCanSendMessages() == PrivacyLevel.FRIEND_ONLY) {
                    if (!areFriends(senderId, recipientId)) {
                        throw new ForbiddenException(ErrorCode.FRIENDS_ONLY_MESSAGES);
                    }
                }
                if (receiverSetting != null && Boolean.TRUE.equals(receiverSetting.getBlockStrangerMessages())) {
                    if (!areFriends(senderId, recipientId)) {
                        throw new ForbiddenException(ErrorCode.STRANGERS_BLOCKED);
                    }
                }
                if (conv.getConversationStatus() == ConversationStatus.NORMAL
                        || conv.getConversationStatus() == null) {
                    if (!areFriends(senderId, recipientId)) {
                        conv.setConversationStatus(ConversationStatus.PENDING);
                    }
                }
            }
        }
        if (conv.getConversationType() == ConversationType.GROUP) {
            ConversationMember requester = conversationMemberRepository.findByConversationIdAndUserId(convId, senderId)
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
            if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.DEPUTY) {
                iuh.fit.entity.ConversationPermission permission = conversationPermissionRepository
                        .findByConversationId(convId).orElse(null);
                if (permission != null && Boolean.FALSE.equals(permission.getCanSendMessages())) {
                    throw new ForbiddenException(ErrorCode.GROUP_MESSAGES_DISABLED);
                }
            }
        }
    }

    private boolean areFriends(String userA, String userB) {
        return friendshipRepository.findByRequesterIdAndReceiverId(userA, userB)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED).isPresent()
                || friendshipRepository.findByRequesterIdAndReceiverId(userB, userA)
                        .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED).isPresent();
    }

    private Message buildMessage(SendMessageRequest request, String senderId, String convId,
            String content, MessageType type, LocalDateTime now) {
        String forwardedFromMessageId = null;
        String forwardedFromSenderId = null;
        if (request.getForwardedFromMessageId() != null && !request.getForwardedFromMessageId().isEmpty()) {
            Message originalMsg = messageBucketService.findMessageById(request.getForwardedFromMessageId())
                    .orElse(null);
            if (originalMsg != null) {
                forwardedFromMessageId = originalMsg.getMessageId();
                forwardedFromSenderId = originalMsg.getSenderId();
            }
        }
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(convId)
                .senderId(senderId)
                .content(content)
                .caption(request.getCaption())
                .messageType(type)
                .replyToMessageId(request.getReplyToMessageId())
                .isEdited(false)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .voiceDuration(request.getVoiceDuration())
                .videoDuration(type == MessageType.VIDEO ? request.getVideoDuration() : null)
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .forwardedFromMessageId(forwardedFromMessageId)
                .forwardedFromSenderId(forwardedFromSenderId)
                .mentions(request.getMentions())
                .storyId(request.getStoryId())
                .build();
    }

    private void persistMessageToCache(Message message, MessageType type, String content, SendMessageRequest request) {
        if (type == MessageType.LINK && isSsrfSafe(content)) {
            try {
                LinkScraper.LinkMetadata metadata = linkScraper.scrape(content);
                message.setLinkTitle(metadata.getTitle());
                message.setLinkThumbnail(metadata.getThumbnail());
            } catch (Exception e) {
                log.warn("Error scraping metadata during message send: {}", e.getMessage());
            }
        }
        if (type == MessageType.IMAGE_GROUP && request.getMediaUrls() != null
                && !request.getMediaUrls().isEmpty()) {
            for (String url : request.getMediaUrls()) {
                messageAttachmentRepository.save(MessageAttachment.builder()
                        .messageId(message.getMessageId())
                        .url(url)
                        .build());
            }
        }
        messageCacheService.pushMessage(message);
        messageProducerService.send(message);
    }

    private void updateConversationLastMessage(Conversations conv, Message message, String senderId) {
        String snippet = switch (message.getMessageType()) {
            case IMAGE -> "[Hình ảnh]";
            case IMAGE_GROUP -> "[Album ảnh]";
            case VIDEO -> "[Video]";
            case MEDIA -> "[File]";
            case VOICE -> "[Tin nhắn thoại]";
            case SHARE_CONTACT -> "📇 Danh thiếp";
            case CALL_MISSED -> "[Cuộc gọi nhỡ]";
            case CALL_REJECTED -> "[Cuộc gọi bị từ chối]";
            case CALL_ENDED -> "[Cuộc gọi]";
            case SYSTEM -> message.getContent();
            default -> message.getContent();
        };
        conv.setLastMessageId(message.getMessageId());
        conv.setLastMessageContent(snippet);
        conv.setLastMessageTime(message.getCreatedAt());
        conv.setLastMessageSenderId(senderId);
        userDetailRepository.findByUserId(senderId)
                .ifPresent(d -> conv.setLastMessageSenderName(d.getDisplayName()));
        conv.setUpdatedAt(message.getCreatedAt());
        conversationRepository.save(conv);
    }

    private void autoUnhideMembers(List<ConversationMember> members, String convId) {
        List<ConversationMember> toUnhide = new ArrayList<>();
        for (ConversationMember member : members) {
            if (Boolean.TRUE.equals(member.getIsHidden())) {
                member.setIsHidden(false);
                toUnhide.add(member);
                Map<String, Object> unhideEvent = new HashMap<>();
                unhideEvent.put("type", "CONVERSATION_UNHIDDEN");
                unhideEvent.put("conversationId", convId);
                messagingTemplate.convertAndSend("/topic/conversation-events/" + member.getUserId(), unhideEvent);
            }
        }
        if (!toUnhide.isEmpty()) {
            conversationMemberRepository.saveAll(toUnhide);
        }
    }

    private void broadcastMessage(MessageAndConversationResponse finalResponse, MessageResponse msgResponse,
            ConversationResponse convResponse, String convId, String senderId,
            List<ConversationMember> members, Conversations conv, String content) {
        messagingTemplate.convertAndSend("/topic/chat/" + convId, finalResponse);

        Map<String, Object> senderSyncEvent = new HashMap<>();
        senderSyncEvent.put("type", "MESSAGE_SYNC");
        senderSyncEvent.put("message", msgResponse);
        senderSyncEvent.put("conversation", convResponse);
        messagingTemplate.convertAndSendToUser(senderId, "/queue/notifications", senderSyncEvent);

        boolean isPendingConv = conv.getConversationStatus() == ConversationStatus.PENDING;
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(senderId)) {
                boolean isMuted = member.getMutedUntil() != null
                        && member.getMutedUntil().isAfter(LocalDateTime.now());
                if (isPendingConv) {
                    Map<String, Object> maskedNotification = new HashMap<>();
                    maskedNotification.put("type", "MESSAGE_REQUEST");
                    maskedNotification.put("conversationId", convId);
                    maskedNotification.put("senderName", msgResponse.getSenderId());
                    maskedNotification.put("text", "Bạn có một tin nhắn chờ mới");
                    maskedNotification.put("muted", isMuted);
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/notifications",
                            maskedNotification);
                } else {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("message", msgResponse);
                    notification.put("conversation", convResponse);
                    notification.put("muted", isMuted);
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/notifications", notification);
                }
            }
        }

        if (msgResponse.getMentions() != null && !msgResponse.getMentions().isEmpty()) {
            UserDetail senderDetail = userDetailRepository.findByUserId(senderId).orElse(null);
            String senderName = senderDetail != null ? senderDetail.getDisplayName() : "Ai đó";
            String groupName = conv.getConversationName() != null ? conv.getConversationName() : "";
            for (String mentionedUserId : msgResponse.getMentions()) {
                if (!mentionedUserId.equals(senderId)) {
                    Map<String, Object> mentionNotification = new HashMap<>();
                    mentionNotification.put("type", "MENTION");
                    mentionNotification.put("conversationId", convId);
                    mentionNotification.put("messageId", msgResponse.getMessageId());
                    mentionNotification.put("senderName", senderName);
                    mentionNotification.put("groupName", groupName);
                    mentionNotification.put("text", content);
                    messagingTemplate.convertAndSendToUser(mentionedUserId, "/queue/notifications",
                            mentionNotification);
                }
            }
        }
    }

    /**
     * Hybrid read with Smart Fallback:
     * 1. Try Redis cache first for page 0
     * 2. If cache has fewer than pageSize items, fetch remainder from DB and merge
     * 3. Trigger warm-up when cache is empty or critically short
     * 4. Total count always comes from bucket (authoritative source)
     */
    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable, String userId) {
        int pageSize = pageable.getPageSize();
        long totalInBucket = messageBucketService.countMessages(conversationId);

        // Page 0 = most recent → try Redis first with smart fallback
        if (pageable.getPageNumber() == 0 && pageSize <= MessageCacheService.MAX_CACHED) {
            List<Message> cached = messageCacheService.getRecentMessages(conversationId, pageSize);

            if (!cached.isEmpty() && cached.size() >= pageSize) {
                // Cache fully satisfies the request — filter localDeletedBy
                List<MessageResponse> responses = cached.stream()
                        .filter(m -> userId == null || m.getLocalDeletedBy() == null
                                || !m.getLocalDeletedBy().contains(userId))
                        .map(messageMapper::toResponse)
                        .collect(Collectors.toList());
                return new org.springframework.data.domain.PageImpl<>(responses, pageable,
                        Math.max(totalInBucket, responses.size()));
            }

            if (!cached.isEmpty() && cached.size() < pageSize && totalInBucket > cached.size()) {
                // Smart Fallback: cache is short → fetch remainder from DB, merge & dedupe
                int deficit = pageSize - cached.size();
                Page<Message> bucketPage = messageBucketService.getConversationMessages(
                        conversationId, PageRequest.of(0, pageSize));
                List<Message> bucketMsgs = bucketPage.getContent();

                // Merge: combine cached + bucket, dedupe by messageId, sort DESC
                java.util.Map<String, Message> merged = new java.util.LinkedHashMap<>();
                for (Message m : cached) {
                    merged.put(m.getMessageId(), m);
                }
                for (Message m : bucketMsgs) {
                    merged.putIfAbsent(m.getMessageId(), m);
                }
                List<Message> mergedList = new java.util.ArrayList<>(merged.values());
                mergedList.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                if (mergedList.size() > pageSize) {
                    mergedList = mergedList.subList(0, pageSize);
                }

                // Warm-up cache with the full merged result
                messageCacheService.warmUp(conversationId, mergedList);

                List<MessageResponse> responses = mergedList.stream()
                        .filter(m -> userId == null || m.getLocalDeletedBy() == null
                                || !m.getLocalDeletedBy().contains(userId))
                        .map(messageMapper::toResponse)
                        .collect(Collectors.toList());
                return new org.springframework.data.domain.PageImpl<>(responses, pageable,
                        Math.max(totalInBucket, responses.size()));
            }

            // Cache empty → fall through to DB and trigger warm-up below
        }

        // Fall back to bucket (primary DB storage)
        Page<Message> bucketPage = messageBucketService.getConversationMessages(conversationId, pageable);
        if (bucketPage.getTotalElements() > 0) {
            // Warm-up cache on page 0 when cache was empty
            if (pageable.getPageNumber() == 0) {
                messageCacheService.warmUp(conversationId, bucketPage.getContent());
            }
            List<MessageResponse> responses = bucketPage.getContent().stream()
                    .filter(m -> userId == null || m.getLocalDeletedBy() == null
                            || !m.getLocalDeletedBy().contains(userId))
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList());
            return new org.springframework.data.domain.PageImpl<>(responses, bucketPage.getPageable(),
                    bucketPage.getTotalElements());
        }
        // Empty page when no messages found in bucket
        return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
    }

    /**
     * Cursor-based pagination: get messages older than {@code beforeId}.
     * Hybrid read: Redis cache → MongoDB bucket → legacy message collection.
     */
    public Page<MessageResponse> getMessagesBefore(String conversationId, String beforeId, int size, String userId) {
        // Find cursor message from bucket first, fallback to message collection
        Message beforeMsg = messageBucketService.findMessageById(beforeId).orElse(null);
        if (beforeMsg == null) {
            return Page.empty();
        }

        long beforeMs = beforeMsg.getCreatedAt()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        // 1. Try Redis cache first (if cursor is within cached window)
        long oldestCached = messageCacheService.oldestCachedTimestamp(conversationId);
        if (beforeMs > oldestCached) {
            List<Message> cached = messageCacheService.getMessagesBefore(conversationId, beforeMs, size);
            if (!cached.isEmpty()) {
                List<MessageResponse> responses = cached.stream()
                        .filter(m -> userId == null || m.getLocalDeletedBy() == null
                                || !m.getLocalDeletedBy().contains(userId))
                        .map(messageMapper::toResponse)
                        .collect(Collectors.toList());
                long estimatedTotal = (responses.size() >= size) ? (long) size * 2 : responses.size();
                return new org.springframework.data.domain.PageImpl<>(responses, PageRequest.of(0, size),
                        estimatedTotal);
            }
        }

        // 2. Fall back to MongoDB bucket aggregation
        List<Message> bucketMsgs = messageBucketService.getMessagesBefore(
                conversationId, beforeMsg.getCreatedAt(), size);
        if (!bucketMsgs.isEmpty()) {
            List<MessageResponse> responses = bucketMsgs.stream()
                    .filter(m -> userId == null || m.getLocalDeletedBy() == null
                            || !m.getLocalDeletedBy().contains(userId))
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList());
            long estimatedTotal = (responses.size() >= size) ? (long) size * 2 : responses.size();
            return new org.springframework.data.domain.PageImpl<>(responses, PageRequest.of(0, size),
                    estimatedTotal);
        }

        // No fallback — bucket is authoritative
        return new org.springframework.data.domain.PageImpl<>(List.of(), PageRequest.of(0, size), 0);
    }

    /**
     * Returns up to {@code halfSize} messages before and {@code halfSize} after the
     * target message, plus the target itself — sorted ascending by createdAt.
     * Used for the "jump to searched message" feature.
     */
    public List<MessageResponse> getMessagesAround(String conversationId, String targetId, int halfSize) {
        Message targetMsg = messageBucketService.findMessageById(targetId).orElse(null);
        if (targetMsg == null) {
            return List.of();
        }

        LocalDateTime targetTime = targetMsg.getCreatedAt();

        // Messages before (returned desc by getMessagesBefore, so reverse to asc)
        List<Message> beforeDesc = messageBucketService.getMessagesBefore(conversationId, targetTime, halfSize);
        List<Message> beforeAsc = new java.util.ArrayList<>(beforeDesc);
        java.util.Collections.reverse(beforeAsc);

        // Messages after (asc)
        List<Message> afterAsc = messageBucketService.getMessagesAfter(conversationId, targetTime, halfSize);

        List<Message> all = new java.util.ArrayList<>();
        all.addAll(beforeAsc);
        all.add(targetMsg);
        all.addAll(afterAsc);

        return all.stream().map(messageMapper::toResponse).collect(Collectors.toList());
    }

    public List<MessageResponse> getConversationMedia(String conversationId) {
        List<String> mediaTypes = Arrays.asList(
                MessageType.IMAGE.name(),
                MessageType.VIDEO.name(),
                MessageType.MEDIA.name());
        List<Message> bucketResults = messageBucketService.findByConversationIdAndMessageTypeIn(conversationId,
                mediaTypes);
        return bucketResults.stream().map(messageMapper::toResponse).collect(Collectors.toList());
    }

    public List<MessageResponse> getConversationLinks(String conversationId) {
        List<Message> bucketResults = messageBucketService.findLinksByConversationId(conversationId);
        return bucketResults.stream().map(messageMapper::toResponse).collect(Collectors.toList());
    }

    // ── Time limits (minutes) ──
    private static final int EDIT_TIME_LIMIT_MINUTES = 15;
    private static final int RECALL_TIME_LIMIT_MINUTES = 60;

    @Transactional
    public MessageResponse updateMessage(String messageId, String content, String userId) {
        Message message = messageBucketService.findMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CANNOT_EDIT_MESSAGE);
        }

        if (Boolean.TRUE.equals(message.getIsRecalled())) {
            throw new AppException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }

        // Time limit check
        if (message.getCreatedAt() != null
                && message.getCreatedAt().plusMinutes(EDIT_TIME_LIMIT_MINUTES).isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.MESSAGE_EDIT_TIME_EXCEEDED);
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

        // Persist to bucket (primary storage)
        messageBucketService.syncMessageUpdate(message);
        messageCacheService.evict(message.getConversationId());
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
        Message message = messageBucketService.findMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.MESSAGE_SENDER_ONLY);
        }

        if (Boolean.TRUE.equals(message.getIsRecalled())) {
            throw new AppException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }

        // Time limit check
        if (message.getCreatedAt() != null
                && message.getCreatedAt().plusMinutes(RECALL_TIME_LIMIT_MINUTES).isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.MESSAGE_RECALL_TIME_EXCEEDED);
        }

        message.setIsRecalled(true);
        message.setUpdatedAt(LocalDateTime.now());

        // Persist to bucket (primary storage)
        messageBucketService.syncMessageRecall(messageId, message.getUpdatedAt());
        messageCacheService.evict(message.getConversationId());
        log.info("Message recalled: {}", messageId);

        MessageResponse response = messageMapper.toResponse(message);

        // Broadcast recall event via WebSocket
        Map<String, Object> recallEvent = new HashMap<>();
        recallEvent.put("type", "MESSAGE_RECALL");
        recallEvent.put("messageId", messageId);
        recallEvent.put("conversationId", message.getConversationId());
        recallEvent.put("senderId", userId);
        recallEvent.put("isRecalled", true);
        messagingTemplate.convertAndSend("/topic/chat/" + message.getConversationId(), recallEvent);

        return response;
    }

    @Transactional
    public void deleteMessage(String messageId, String userId) {
        Message message = messageBucketService.findMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DELETE_MESSAGE);
        }

        // Persist to bucket (primary storage)
        messageBucketService.syncMessageDelete(messageId);
        messageCacheService.evict(message.getConversationId());
        log.info("Message deleted: {}", messageId);
    }

    /**
     * Delete message locally (only for the requesting user).
     * The message remains visible to other participants.
     */
    @Transactional
    public void deleteMessageLocal(String messageId, String userId) {
        Message message = messageBucketService.findMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (message.getLocalDeletedBy() == null) {
            message.setLocalDeletedBy(new java.util.ArrayList<>());
        }
        if (!message.getLocalDeletedBy().contains(userId)) {
            message.getLocalDeletedBy().add(userId);
        }

        // Persist to bucket (primary storage)
        messageBucketService.syncMessageLocalDelete(messageId, message.getLocalDeletedBy());
        messageCacheService.evict(message.getConversationId());
        log.info("Message locally deleted for user {}: {}", userId, messageId);

        // Broadcast to all sessions of this user so every device hides the message
        // immediately
        Map<String, Object> deleteEvent = new HashMap<>();
        deleteEvent.put("type", "MESSAGE_LOCAL_DELETE");
        deleteEvent.put("messageId", messageId);
        deleteEvent.put("conversationId", message.getConversationId());
        deleteEvent.put("userId", userId);
        messagingTemplate.convertAndSend("/topic/conversation-events/" + userId, deleteEvent);
    }

    public void addReaction(String messageId, String userId, ReactionType reactionType) {
        // Tìm message trong bucket (primary storage) → fallback legacy collection
        Message message = messageBucketService.findMessageById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.message(messageId));

        // Toggle: nếu reaction cùng (messageId, userId, icon) đã tồn tại → xóa (toggle
        // off)
        // nếu chưa → thêm mới (toggle on)
        String action;
        String reactionId;
        java.util.Optional<MessageReaction> existing = messageReactionRepository
                .findByMessageIdAndUserIdAndIcon(messageId, userId, reactionType);

        if (existing.isPresent()) {
            messageReactionRepository.deleteById(existing.get().getId());
            action = "REMOVE";
            reactionId = existing.get().getId();
            log.info("Removed reaction {} from message: {} by user: {}", reactionType, messageId, userId);
        } else {
            MessageReaction newReaction = MessageReaction.builder()
                    .id(UUID.randomUUID().toString())
                    .messageId(messageId)
                    .userId(userId)
                    .icon(reactionType)
                    .build();
            messageReactionRepository.save(newReaction);
            action = "ADD";
            reactionId = newReaction.getId();
            log.info("Added reaction {} (ID: {}) to message: {} by user: {}", reactionType, reactionId, messageId,
                    userId);
        }

        // Broadcast reaction event via websocket
        UserDetail reactor = userDetailRepository.findById(userId).orElse(null);

        Map<String, Object> reactionEvent = new HashMap<>();
        reactionEvent.put("type", "REACTION_UPDATE");
        reactionEvent.put("messageId", messageId);
        reactionEvent.put("userId", userId);
        reactionEvent.put("userName", reactor != null ? reactor.getDisplayName() : "Unknown");
        reactionEvent.put("userAvatar", reactor != null ? reactor.getAvatarUrl() : null);
        reactionEvent.put("reactionId", reactionId);
        reactionEvent.put("action", action);
        reactionEvent.put("reactionType", reactionType);

        messagingTemplate.convertAndSend("/topic/chat/" + message.getConversationId(), reactionEvent);
    }

    /**
     * Clear ALL messages in a SELF (AI or My Documents) conversation.
     *
     * <p>
     * Ownership check: the caller must be a recorded member of the conversation.
     * Scope restriction: only SELF-type conversations may be cleared to avoid
     * accidental wipes of shared group / private chats.
     *
     * <p>
     * Operation order (best-effort S3 then atomic DB deletions):
     * <ol>
     * <li>Verify conversation exists and caller is a member.</li>
     * <li>Collect all messages and S3 media URLs.</li>
     * <li>Delete S3 objects in parallel (non-blocking, failures are logged).</li>
     * <li>Hard-delete reactions, attachments, pinned refs, then messages.</li>
     * <li>Reset conversation last-message metadata.</li>
     * </ol>
     *
     * @param conversationId target conversation
     * @param userId         authenticated user performing the action
     */
    @Transactional
    public void clearConversationAll(String conversationId, String userId) {
        // 1. Verify conversation exists
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CONVERSATION_NOT_FOUND,
                        "Kh\u00f4ng t\u00ecm th\u1ea5y h\u1ed9i tho\u1ea1i: " + conversationId));

        // 2. Ownership check: caller must be a registered member
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(ForbiddenException::notConversationMember);

        // 3. Restrict to personal (SELF) conversations only
        if (conv.getConversationType() != ConversationType.SELF) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Ch\u1ec9 c\u00f3 th\u1ec3 x\u00f3a to\u00e0n b\u1ed9 h\u1ed9i tho\u1ea1i c\u00e1 nh\u00e2n ho\u1eb7c AI");
        }

        // 4. Fetch all messages from buckets (primary storage)
        List<Message> messages = messageBucketService.findAllByConversationId(conversationId);
        if (messages.isEmpty()) {
            log.info("clearConversationAll: no messages found in conversation {}", conversationId);
            return;
        }

        List<String> messageIds = messages.stream()
                .map(Message::getMessageId)
                .collect(Collectors.toList());

        // 5. Collect S3 media URLs from messages (IMAGE, VIDEO, MEDIA, VOICE types)
        List<String> s3Urls = messages.stream()
                .filter(m -> m.getContent() != null
                        && m.getContent().contains(".amazonaws.com/")
                        && m.getMessageType() != null
                        && m.getMessageType() != MessageType.TEXT
                        && m.getMessageType() != MessageType.LINK)
                .map(Message::getContent)
                .collect(Collectors.toList());

        // 6. Delete S3 files in parallel — best-effort, failures are logged but do not
        // abort the DB cleanup so the conversation is still usable afterwards.
        if (!s3Urls.isEmpty()) {
            storageService.deleteObjectsByUrls(s3Urls);
            log.info("clearConversationAll: queued {} S3 objects for deletion", s3Urls.size());
        }

        // 7. Remove all child documents before the parent messages
        // (MongoDB has no FK cascade, so order matters)
        messageReactionRepository.deleteByMessageIdIn(messageIds);
        messageAttachmentRepository.deleteByMessageIdIn(messageIds);
        pinnedMessageRepository.deleteByConversationId(conversationId);

        // Delete from bucket storage (primary)
        messageBucketService.deleteByConversationId(conversationId);

        // 8. Reset conversation last-message metadata so the UI shows empty state
        conv.setLastMessageId(null);
        conv.setLastMessageContent(null);
        conv.setLastMessageTime(null);
        conversationRepository.save(conv);

        log.info("clearConversationAll: deleted {} messages from conversation {} by user {}",
                messages.size(), conversationId, userId);
    }
}
