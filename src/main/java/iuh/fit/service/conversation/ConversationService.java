package iuh.fit.service.conversation;

import iuh.fit.dto.request.conversation.CreateConversationRequest;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Message;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.MemberRole;
import iuh.fit.enums.MessageType;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.InvalidInputException;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final ConversationMapper conversationMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Tìm cuộc hội thoại P2P giữa 2 người. Trả về null nếu chưa có (Lazy Creation).
     */
    @Transactional(readOnly = true)
    public ConversationResponse getPrivateConversation(String userId, String friendId) {
        List<String> sortedParticipants = Stream.of(userId, friendId)
                .sorted()
                .collect(Collectors.toList());

        return conversationRepository.findPrivateConversation(sortedParticipants)
                .map(conv -> {
                    enrichWithLastMessage(conv);
                    List<ConversationMember> members = conversationMemberRepository
                            .findByConversationId(conv.getConversationId());
                    return conversationMapper.toResponse(conv, members);
                })
                .orElse(null);
    }

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(String user1Id, String user2Id) {
        ConversationResponse existing = getPrivateConversation(user1Id, user2Id);
        if (existing != null)
            return existing;

        // Nếu chưa có, tạo mới
        List<String> sortedParticipants = Stream.of(user1Id, user2Id)
                .sorted()
                .collect(Collectors.toList());

        Conversations newConv = Conversations.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ConversationType.PRIVATE)
                .participants(sortedParticipants)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        final Conversations savedConv = conversationRepository.save(newConv);

        // Thêm member
        List<ConversationMember> members = sortedParticipants.stream().map(uid -> ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(savedConv.getConversationId())
                .userId(uid)
                .joinedAt(LocalDateTime.now())
                .role(MemberRole.MEMBER)
                .build()).collect(Collectors.toList());

        conversationMemberRepository.saveAll(members);

        log.info("Created new private conversation between {} and {}", user1Id, user2Id);
        enrichWithLastMessage(savedConv);
        return conversationMapper.toResponse(savedConv, members);
    }

    @Transactional
    public ConversationResponse createGroupConversation(String creatorId, CreateConversationRequest request) {
        // Collect member IDs for notification (do NOT set participants on GROUP —
        // unique index is for PRIVATE only)
        List<String> allMemberIds = new ArrayList<>();
        allMemberIds.add(creatorId);
        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (!memberId.equals(creatorId) && !allMemberIds.contains(memberId)) {
                    allMemberIds.add(memberId);
                }
            }
        }

        Conversations group = Conversations.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ConversationType.GROUP)
                .conversationName(request.getConversationName())
                .avatarUrl(request.getConversationAvatarUrl())
                .creatorId(creatorId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        group = conversationRepository.save(group);

        List<ConversationMember> members = new ArrayList<>();

        // Thêm người tạo làm ADMIN
        members.add(ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(group.getConversationId())
                .userId(creatorId)
                .role(MemberRole.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build());

        // Thêm các thành viên khác
        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (!memberId.equals(creatorId)) {
                    members.add(ConversationMember.builder()
                            .id(UUID.randomUUID().toString())
                            .conversationId(group.getConversationId())
                            .userId(memberId)
                            .role(MemberRole.MEMBER)
                            .joinedAt(LocalDateTime.now())
                            .build());
                }
            }
        }

        members = conversationMemberRepository.saveAll(members);
        log.info("Created new group conversation: {}", group.getConversationName());

        enrichWithLastMessage(group);
        ConversationResponse response = conversationMapper.toResponse(group, members);

        // Notify all members via personal topic so their Sidebar updates instantly
        for (String memberId : allMemberIds) {
            messagingTemplate.convertAndSend(
                    "/topic/group-events/" + memberId, response);
        }

        return response;
    }

    public List<ConversationResponse> getUserConversations(String userId) {
        List<ConversationMember> memberships = conversationMemberRepository.findByUserId(userId);
        return memberships.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsHidden())) // Skip hidden (soft-deleted) conversations
                .map(m -> {
                    Conversations conv = conversationRepository.findById(m.getConversationId()).orElse(null);
                    if (conv == null || Boolean.TRUE.equals(conv.getIsDeleted()))
                        return null;

                    enrichWithLastMessage(conv);

                    List<ConversationMember> members = conversationMemberRepository
                            .findByConversationId(conv.getConversationId());
                    return conversationMapper.toResponse(conv, members, userId);
                })
                .filter(resp -> resp != null)
                .sorted((c1, c2) -> {
                    // Pinned conversations first, then by time
                    boolean p1 = Boolean.TRUE.equals(c1.getIsPinned());
                    boolean p2 = Boolean.TRUE.equals(c2.getIsPinned());
                    if (p1 != p2)
                        return p1 ? -1 : 1;

                    LocalDateTime t1 = c1.getLastMessageTime() != null ? c1.getLastMessageTime() : c1.getCreatedAt();
                    LocalDateTime t2 = c2.getLastMessageTime() != null ? c2.getLastMessageTime() : c2.getCreatedAt();
                    return t2.compareTo(t1); // Sort descending (newest first)
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationResponse getOrCreateSelfConversation(String userId) {
        // Find existing SELF conversation for user
        List<ConversationMember> userConvs = conversationMemberRepository.findByUserId(userId);
        for (ConversationMember member : userConvs) {
            Optional<Conversations> convOpt = conversationRepository.findById(member.getConversationId());
            if (convOpt.isPresent() && convOpt.get().getConversationType() == ConversationType.SELF) {
                Conversations conv = convOpt.get();
                enrichWithLastMessage(conv);
                return conversationMapper.toResponse(conv, List.of(member));
            }
        }

        // Create new SELF conversation if not exists
        Conversations newConv = Conversations.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ConversationType.SELF)
                .conversationName("Cloud của tôi")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        newConv = conversationRepository.save(newConv);

        ConversationMember member = ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(newConv.getConversationId())
                .userId(userId)
                .joinedAt(LocalDateTime.now())
                .role(MemberRole.ADMIN) // Single user has ADMIN role
                .build();
        conversationMemberRepository.save(member);

        log.info("Created new SELF conversation (Cloud) for user {}", userId);
        enrichWithLastMessage(newConv);
        return conversationMapper.toResponse(newConv, List.of(member));
    }

    private void enrichWithLastMessage(Conversations conv) {
        if (conv == null)
            return;
        if (conv.getLastMessageContent() == null) {
            messageRepository.findByConversationIdOrderByCreatedAtDesc(conv.getConversationId(), PageRequest.of(0, 1))
                    .getContent().stream().findFirst().ifPresent(lastMsg -> {
                        String snippet = lastMsg.getContent();
                        if (lastMsg.getMessageType() == MessageType.IMAGE)
                            snippet = "[Hình ảnh]";
                        else if (lastMsg.getMessageType() == MessageType.VIDEO)
                            snippet = "[Video]";
                        else if (lastMsg.getMessageType() == MessageType.MEDIA)
                            snippet = "[File]";

                        conv.setLastMessageId(lastMsg.getMessageId());
                        conv.setLastMessageContent(snippet);
                        conv.setLastMessageTime(lastMsg.getCreatedAt());
                        if (conv.getUpdatedAt() == null) {
                            conv.setUpdatedAt(lastMsg.getCreatedAt());
                        }
                    });
        }
    }

    // ==================== GROUP MEMBER MANAGEMENT ====================

    /**
     * Get all members of a group conversation.
     * Lấy danh sách thành viên trong nhóm.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse.MemberInfo> getGroupMembers(String conversationId, String userId) {
        // Validate user is a member
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của nhóm này"));

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        return members.stream()
                .map(m -> conversationMapper.toMemberInfo(m))
                .collect(Collectors.toList());
    }

    /**
     * Add members to a group. Only ADMIN/DEPUTY can add.
     * Thêm thành viên vào nhóm (chỉ Admin/Phó nhóm mới có quyền).
     */
    @Transactional
    public ConversationResponse addMembers(String conversationId, List<String> newMemberIds, String requesterId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new RuntimeException("Chỉ có thể thêm thành viên vào nhóm chat");
        }

        // Check requester has ADMIN or DEPUTY role
        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của nhóm này"));
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.DEPUTY) {
            throw new RuntimeException("Chỉ Admin hoặc Phó nhóm mới có quyền thêm thành viên");
        }

        List<ConversationMember> existingMembers = conversationMemberRepository.findByConversationId(conversationId);
        List<String> existingIds = existingMembers.stream().map(ConversationMember::getUserId)
                .collect(Collectors.toList());

        List<ConversationMember> newMembers = new ArrayList<>();
        for (String memberId : newMemberIds) {
            if (!existingIds.contains(memberId)) {
                newMembers.add(ConversationMember.builder()
                        .id(UUID.randomUUID().toString())
                        .conversationId(conversationId)
                        .userId(memberId)
                        .role(MemberRole.MEMBER)
                        .joinedAt(LocalDateTime.now())
                        .build());
            }
        }

        if (!newMembers.isEmpty()) {
            conversationMemberRepository.saveAll(newMembers);
            log.info("Added {} new members to group {}", newMembers.size(), conversationId);

            // Notify new members
            for (ConversationMember m : newMembers) {
                enrichWithLastMessage(conv);
                List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
                ConversationResponse response = conversationMapper.toResponse(conv, allMembers);
                messagingTemplate.convertAndSend("/topic/group-events/" + m.getUserId(), response);
            }
        }

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        enrichWithLastMessage(conv);
        return conversationMapper.toResponse(conv, allMembers);
    }

    /**
     * Remove a member from a group. Only ADMIN can remove.
     * Xóa thành viên khỏi nhóm (chỉ Admin mới có quyền).
     */
    @Transactional
    public ConversationResponse removeMember(String conversationId, String memberToRemoveId, String requesterId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new RuntimeException("Chỉ có thể xóa thành viên khỏi nhóm chat");
        }

        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của nhóm này"));

        if (requester.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Chỉ Admin mới có quyền xóa thành viên");
        }

        if (memberToRemoveId.equals(requesterId)) {
            throw new RuntimeException("Admin không thể tự xóa chính mình. Hãy chuyển quyền Admin trước khi rời nhóm");
        }

        ConversationMember memberToRemove = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, memberToRemoveId)
                .orElseThrow(() -> new RuntimeException("Người dùng không phải thành viên của nhóm"));

        conversationMemberRepository.delete(memberToRemove);
        log.info("Removed member {} from group {}", memberToRemoveId, conversationId);

        // Notify removed member
        messagingTemplate.convertAndSend("/topic/group-events/" + memberToRemoveId,
                java.util.Map.of("type", "REMOVED", "conversationId", conversationId));

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        enrichWithLastMessage(conv);
        return conversationMapper.toResponse(conv, allMembers);
    }

    // ==================== ROLE MANAGEMENT ====================

    /**
     * Change a member's role. Only ADMIN can promote/demote.
     * Thay đổi quyền thành viên. Chỉ ADMIN mới có quyền.
     *
     * @param conversationId ID nhóm
     * @param targetUserId   ID thành viên cần thay đổi quyền
     * @param newRole        Quyền mới (DEPUTY hoặc MEMBER)
     * @param requesterId    ID người yêu cầu (phải là ADMIN)
     */
    @Transactional
    public ConversationResponse.MemberInfo changeMemberRole(
            String conversationId, String targetUserId, MemberRole newRole, String requesterId) {
        if (newRole == MemberRole.ADMIN) {
            throw new InvalidInputException(ErrorCode.CANNOT_LEAVE_CONVERSATION,
                    "Không thể phong ADMIN bằng API này. Hãy dùng chức năng chuyển quyền Trưởng nhóm.");
        }

        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của nhóm này"));
        if (requester.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Chỉ Trưởng nhóm mới có quyền thay đổi vai trò thành viên");
        }

        if (targetUserId.equals(requesterId)) {
            throw new RuntimeException("Không thể thay đổi quyền của chính mình");
        }

        ConversationMember target = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new RuntimeException("Người dùng không phải thành viên của nhóm"));

        MemberRole oldRole = target.getRole();
        target.setRole(newRole);
        conversationMemberRepository.save(target);

        String roleLabel = newRole == MemberRole.DEPUTY ? "Phó nhóm" : "Thành viên";
        log.info("Changed role of {} from {} to {} in group {}", targetUserId, oldRole, newRole, conversationId);

        // Send system message via WebSocket
        // Gửi thông báo hệ thống qua WebSocket
        ConversationResponse.MemberInfo targetInfo = conversationMapper.toMemberInfo(target);
        String systemMsg = targetInfo.getDisplayName() + " đã được phong làm " + roleLabel;
        broadcastSystemMessage(conversationId, systemMsg);

        return targetInfo;
    }

    /**
     * Transfer ADMIN ownership to another member.
     * Chuyển quyền Trưởng nhóm cho thành viên khác.
     *
     * @param conversationId ID nhóm
     * @param newAdminId     ID thành viên mới sẽ trở thành ADMIN
     * @param requesterId    ID Trưởng nhóm hiện tại
     */
    @Transactional
    public void transferOwnership(String conversationId, String newAdminId, String requesterId) {
        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của nhóm này"));
        if (requester.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Chỉ Trưởng nhóm mới có quyền chuyển nhượng");
        }
        if (newAdminId.equals(requesterId)) {
            throw new RuntimeException("Bạn đã là Trưởng nhóm");
        }

        ConversationMember newAdmin = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, newAdminId)
                .orElseThrow(() -> new RuntimeException("Người dùng không phải thành viên của nhóm"));

        // Demote old admin → MEMBER, promote new admin → ADMIN
        // Hạ quyền admin cũ → MEMBER, phong admin mới → ADMIN
        requester.setRole(MemberRole.MEMBER);
        newAdmin.setRole(MemberRole.ADMIN);
        conversationMemberRepository.save(requester);
        conversationMemberRepository.save(newAdmin);

        log.info("Transferred admin from {} to {} in group {}", requesterId, newAdminId, conversationId);

        ConversationResponse.MemberInfo newAdminInfo = conversationMapper.toMemberInfo(newAdmin);
        String systemMsg = newAdminInfo.getDisplayName() + " đã trở thành Trưởng nhóm mới";
        broadcastSystemMessage(conversationId, systemMsg);
    }

    /**
     * Leave a group conversation.
     * Rời khỏi nhóm. Nếu là Admin, phải chỉ định người kế nhiệm (successorId).
     *
     * @param conversationId ID nhóm
     * @param userId         ID người muốn rời
     * @param successorId    (Tùy chọn) ID người kế nhiệm nếu là Admin
     */
    @Transactional
    public void leaveGroup(String conversationId, String userId, String successorId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new RuntimeException("Chỉ có thể rời khỏi nhóm chat");
        }

        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của nhóm này"));

        if (member.getRole() == MemberRole.ADMIN) {
            List<ConversationMember> otherMembers = conversationMemberRepository.findByConversationId(conversationId)
                    .stream().filter(m -> !m.getUserId().equals(userId)).collect(Collectors.toList());

            if (otherMembers.isEmpty()) {
                // Last member leaving → soft delete the group
                conv.setIsDeleted(true);
                conversationRepository.save(conv);
            } else if (successorId != null && !successorId.isBlank()) {
                // Transfer admin to chosen successor, then leave
                // Chuyển quyền cho người kế nhiệm, rồi rời nhóm
                transferOwnership(conversationId, successorId, userId);
            } else {
                // No successor specified → block
                throw new InvalidInputException(
                        ErrorCode.CANNOT_LEAVE_CONVERSATION,
                        "Trưởng nhóm không thể rời nhóm khi chưa chuyển quyền cho người khác");
            }
        }

        conversationMemberRepository.delete(member);
        log.info("User {} left group {}", userId, conversationId);
    }

    /**
     * Toggle pin/unpin a conversation for a specific user.
     * Ghim / bỏ ghim hội thoại cho một user cụ thể.
     */
    @Transactional
    public java.util.Map<String, Object> togglePinConversation(String conversationId, String userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của hội thoại này"));

        boolean newPinned = !Boolean.TRUE.equals(member.getIsPinned());
        member.setIsPinned(newPinned);
        conversationMemberRepository.save(member);

        log.info("User {} {} conversation {}", userId, newPinned ? "pinned" : "unpinned", conversationId);

        java.util.Map<String, Object> event = java.util.Map.of(
                "type", "PIN_UPDATED",
                "conversationId", conversationId,
                "isPinned", newPinned);
        messagingTemplate.convertAndSend("/topic/conversation-events/" + userId, event);

        return event;
    }

    /**
     * Soft-delete (hide) a conversation for a specific user.
     * Xóa mềm (ẩn) hội thoại cho một user cụ thể. Không ảnh hưởng user khác.
     */
    @Transactional
    public java.util.Map<String, Object> softDeleteConversation(String conversationId, String userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của hội thoại này"));

        member.setIsHidden(true);
        conversationMemberRepository.save(member);

        log.info("User {} soft-deleted (hid) conversation {}", userId, conversationId);

        java.util.Map<String, Object> event = java.util.Map.of(
                "type", "CONVERSATION_DELETED",
                "conversationId", conversationId);
        messagingTemplate.convertAndSend("/topic/conversation-events/" + userId, event);

        return event;
    }

    /**
     * Broadcast a system message to all members in a conversation via WebSocket.
     * Gửi thông báo hệ thống cho tất cả thành viên trong nhóm qua WebSocket.
     */
    private void broadcastSystemMessage(String conversationId, String content) {
        Message msg = Message.builder()
                .conversationId(conversationId)
                .senderId("SYSTEM")
                .messageType(MessageType.SYSTEM)
                .content(content)
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .isRecalled(false)
                .isEdited(false)
                .build();
        messageRepository.save(msg);

        java.util.Map<String, Object> payload = java.util.Map.of(
                "id", msg.getMessageId(),
                "messageId", msg.getMessageId(),
                "type", "SYSTEM",
                "messageType", "SYSTEM",
                "content", content,
                "senderId", "SYSTEM",
                "conversationId", conversationId,
                "createdAt", msg.getCreatedAt().toString());
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, payload);
    }
}
