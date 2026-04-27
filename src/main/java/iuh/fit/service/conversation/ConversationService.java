package iuh.fit.service.conversation;

import iuh.fit.dto.request.conversation.CreateConversationRequest;
import iuh.fit.dto.request.conversation.UpdateConversationRequest;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.ConversationPermission;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.Message;
import iuh.fit.enums.ConversationStatus;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.enums.MemberRole;
import iuh.fit.enums.MessageType;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.ForbiddenException;
import iuh.fit.exception.InvalidInputException;
import iuh.fit.exception.ResourceNotFoundException;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationPermissionRepository;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import iuh.fit.utils.JwtUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final FriendshipRepository friendshipRepository;
    private final iuh.fit.repository.UserDetailRepository userDetailRepository;
    private final ConversationPermissionRepository conversationPermissionRepository;
    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;


    private static final String[] DEFAULT_GROUP_AVATARS = {
            "/avatar_group/avtgr1.jpg",
            "/avatar_group/avtgr2.jpg",
            "/avatar_group/avtgr3.jpg",
            "/avatar_group/avtgr4.jpg",
            "/avatar_group/avtgr5.jpg",
            "/avatar_group/avtgr6.jpg",
            "/avatar_group/avtgr7.jpg",
            "/avatar_group/avtgr8.jpg",
            "/avatar_group/avtgr9.jpg",
            "/avatar_group/avtgr10.jpg",
            "/avatar_group/avtgr11.jpg",
            "/avatar_group/avtgr12.jpg"
    };
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

        LocalDateTime now = LocalDateTime.now();
        Conversations newConv = Conversations.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ConversationType.PRIVATE)
                .participants(sortedParticipants)
                .createdAt(now)
                .updatedAt(now)
                .lastMessageTime(now)
                .isDeleted(false)
                .build();
        final Conversations savedConv = conversationRepository.save(newConv);

        // Thêm member
        List<ConversationMember> members = sortedParticipants.stream()
                .map((String uid) -> ConversationMember.builder()
                        .id(UUID.randomUUID().toString())
                        .conversationId(savedConv.getConversationId())
                        .userId(uid)
                        .joinedAt(LocalDateTime.now())
                        .role(MemberRole.MEMBER)
                        .build())
                .collect(Collectors.toList());

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

        String avatarUrl = request.getConversationAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            int randomIdx = new java.util.Random().nextInt(DEFAULT_GROUP_AVATARS.length);
            avatarUrl = DEFAULT_GROUP_AVATARS[randomIdx];
        }

        String groupId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        Conversations group = Conversations.builder()
                .conversationId(groupId)
                .conversationType(ConversationType.GROUP)
                .conversationName(request.getConversationName())
                .avatarUrl(avatarUrl)
                .creatorId(creatorId)
                .createdAt(now)
                .updatedAt(now)
                .lastMessageTime(now)
                .isDeleted(false)
                .invitationLink("fruvi.chat/" + groupId)
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

        // Create default permissions for group
        ConversationPermission permissions = ConversationPermission.builder()
                .conversationId(groupId)
                .updatedAt(now)
                .build();
        conversationPermissionRepository.save(permissions);

        log.info("Created new group conversation: {}", group.getConversationName());

        enrichWithLastMessage(group);
        ConversationResponse response = conversationMapper.toResponse(group, members, permissions);
        response.setType("CREATED");

        // Notify all members via personal topic so their Sidebar updates instantly
        for (String memberId : allMemberIds) {
            messagingTemplate.convertAndSend(
                    "/topic/group-events/" + memberId, response);
        }

        return response;
    }

    /**
     * Cập nhật thông tin nhóm (tên, avatar, mô tả). Chỉ ADMIN hoặc DEPUTY mới có
     * quyền.
     */
    @Transactional
    public ConversationResponse updateGroupInfo(String conversationId, String userId,
            UpdateConversationRequest request) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new InvalidInputException(ErrorCode.INVALID_INPUT, "Chỉ có thể cập nhật thông tin nhóm chat");
        }

        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        // Check permissions: Admin/Deputy always allowed. Members allowed if canEditInfo is true.
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.DEPUTY) {
            ConversationPermission permission = getPermissions(conversationId);
            if (permission == null || !Boolean.TRUE.equals(permission.getCanEditInfo())) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN,
                        "Chỉ Admin hoặc Phó nhóm mới có quyền cập nhật thông tin nhóm");
            }
        }

        if (request.getConversationName() != null) {
            conv.setConversationName(request.getConversationName());
        }
        if (request.getConversationAvatarUrl() != null) {
            conv.setAvatarUrl(request.getConversationAvatarUrl());
        }
        if (request.getGroupDescription() != null) {
            conv.setGroupDescription(request.getGroupDescription());
        }

        conv.setUpdatedAt(LocalDateTime.now());
        conv = conversationRepository.save(conv);

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        ConversationPermission permission = conversationPermissionRepository.findByConversationId(conversationId).orElse(null);
        enrichWithLastMessage(conv);
        ConversationResponse response = conversationMapper.toResponse(conv, allMembers, permission);
        response.setType("UPDATED");

        // Notify all members about group info update
        for (ConversationMember member : allMembers) {
            messagingTemplate.convertAndSend(
                    "/topic/group-events/" + member.getUserId(), response);
        }

        log.info("Updated group info for conversation {}: name={}, avatar={}, description={}",
                conversationId, request.getConversationName(), request.getConversationAvatarUrl(),
                request.getGroupDescription());

        return response;
    }

    /**
     * Cập nhật quyền hạn nhóm (ai có quyền đổi thông tin, ghim tin nhắn, v.v.).
     * Chỉ ADMIN hoặc DEPUTY mới có quyền.
     */
    @Transactional
    public ConversationResponse updatePermissions(String conversationId, String userId, iuh.fit.dto.request.conversation.UpdatePermissionRequest request) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new InvalidInputException(ErrorCode.INVALID_INPUT, "Chỉ có thể cập nhật quyền hạn cho nhóm chat");
        }

        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.DEPUTY) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Chỉ Admin hoặc Phó nhóm mới có quyền cập nhật quyền hạn nhóm");
        }

        ConversationPermission permission = conversationPermissionRepository.findByConversationId(conversationId)
                .orElseGet(() -> ConversationPermission.builder().conversationId(conversationId).build());

        if (request.getCanEditInfo() != null) permission.setCanEditInfo(request.getCanEditInfo());
        if (request.getCanPinMessages() != null) permission.setCanPinMessages(request.getCanPinMessages());
        if (request.getCanCreateNotes() != null) permission.setCanCreateNotes(request.getCanCreateNotes());
        if (request.getCanCreatePolls() != null) permission.setCanCreatePolls(request.getCanCreatePolls());
        if (request.getCanSendMessages() != null) permission.setCanSendMessages(request.getCanSendMessages());
        if (request.getIsMemberApprovalRequired() != null) permission.setIsMemberApprovalRequired(request.getIsMemberApprovalRequired());
        if (request.getIsHighlightAdminMessages() != null) permission.setIsHighlightAdminMessages(request.getIsHighlightAdminMessages());
        if (request.getCanNewMembersReadRecentMessages() != null) permission.setCanNewMembersReadRecentMessages(request.getCanNewMembersReadRecentMessages());

        permission.setUpdatedAt(LocalDateTime.now());
        permission = conversationPermissionRepository.save(permission);

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        enrichWithLastMessage(conv);
        ConversationResponse response = conversationMapper.toResponse(conv, allMembers, permission);
        response.setType("PERMISSIONS_UPDATED");

        // Notify all members about permissions update
        for (ConversationMember member : allMembers) {
            messagingTemplate.convertAndSend("/topic/group-events/" + member.getUserId(), response);
        }

        log.info("Updated permissions for conversation {}: {}", conversationId, request);
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
                    return conversationMapper.toResponse(conv, members, getPermissions(conv.getConversationId()), userId);
                })
                .filter(resp -> resp != null)
                .sorted((ConversationResponse c1, ConversationResponse c2) -> {
                    // Pinned conversations first, then by time
                    boolean p1 = Boolean.TRUE.equals(c1.getIsPinned());
                    boolean p2 = Boolean.TRUE.equals(c2.getIsPinned());
                    if (p1 != p2)
                        return p1 ? -1 : 1;

                    // If both are pinned, newest pin goes first
                    if (p1 && p2) {
                        LocalDateTime pin1 = c1.getPinnedAt();
                        LocalDateTime pin2 = c2.getPinnedAt();
                        if (pin1 == null && pin2 != null)
                            return 1;
                        if (pin1 != null && pin2 == null)
                            return -1;
                        if (pin1 != null && pin2 != null) {
                            int pinCompare = pin2.compareTo(pin1);
                            if (pinCompare != 0)
                                return pinCompare;
                        }
                    }

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
                .map(m -> conversationMapper.toMemberInfo(m, userId))
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
        // Check requester has ADMIN or DEPUTY role
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
                ConversationResponse response = conversationMapper.toResponse(conv, allMembers, getPermissions(conversationId));
                response.setType("CREATED");
                messagingTemplate.convertAndSend("/topic/group-events/" + m.getUserId(), response);
            }

            // Broadcast system message to all members in chat
            iuh.fit.entity.UserDetail requesterDetail = userDetailRepository.findByUserId(requesterId).orElse(null);
            String requesterName = requesterDetail != null ? requesterDetail.getDisplayName() : "Ai đó";
            
            List<String> names = new ArrayList<>();
            for (ConversationMember m : newMembers) {
                userDetailRepository.findByUserId(m.getUserId()).ifPresent(d -> names.add(d.getDisplayName()));
            }
            if (!names.isEmpty()) {
                String joinedNames = String.join(", ", names);
                broadcastSystemMessage(conversationId, requesterName + " đã thêm " + joinedNames + " vào nhóm");
            }
        }

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        enrichWithLastMessage(conv);
        return conversationMapper.toResponse(conv, allMembers, getPermissions(conversationId));
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

        // Broadcast system message
        iuh.fit.entity.UserDetail requesterDetail = userDetailRepository.findByUserId(requesterId).orElse(null);
        iuh.fit.entity.UserDetail targetDetail = userDetailRepository.findByUserId(memberToRemoveId).orElse(null);
        String rName = requesterDetail != null ? requesterDetail.getDisplayName() : "Ai đó";
        String tName = targetDetail != null ? targetDetail.getDisplayName() : "Thành viên";
        broadcastSystemMessage(conversationId, rName + " đã xóa " + tName + " khỏi nhóm");

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        enrichWithLastMessage(conv);
        return conversationMapper.toResponse(conv, allMembers, getPermissions(conversationId));
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
        ConversationResponse.MemberInfo targetInfo = conversationMapper.toMemberInfo(target, requesterId);
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

        ConversationResponse.MemberInfo newAdminInfo = conversationMapper.toMemberInfo(newAdmin, requesterId);
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

        // Broadcast system message
        iuh.fit.entity.UserDetail detail = userDetailRepository.findByUserId(userId).orElse(null);
        String name = detail != null ? detail.getDisplayName() : "Ai đó";
        broadcastSystemMessage(conversationId, name + " đã rời khỏi nhóm");
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

        if (newPinned) {
            long pinnedCount = conversationMemberRepository.countByUserIdAndIsPinnedTrue(userId);
            if (pinnedCount >= 5) {
                throw new InvalidInputException(
                        ErrorCode.INVALID_INPUT,
                        "Mỗi người chỉ được ghim tối đa 5 cuộc hội thoại");
            }
            member.setPinnedAt(LocalDateTime.now());
        } else {
            member.setPinnedAt(null);
        }

        member.setIsPinned(newPinned);
        conversationMemberRepository.save(member);

        log.info("User {} {} conversation {}", userId, newPinned ? "pinned" : "unpinned", conversationId);

        HashMap<String, Object> event = new HashMap<>();
        event.put("type", "PIN_UPDATED");
        event.put("conversationId", conversationId);
        event.put("isPinned", newPinned);
        event.put("pinnedAt", member.getPinnedAt() != null ? member.getPinnedAt().toString() : null);
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

    // ==================== NICKNAME MANAGEMENT ====================

    /**
     * Update the nickname for a contact/member in a conversation.
     * Cập nhật biệt danh cho thành viên trong cuộc hội thoại.
     * Biệt danh chỉ hiển thị cho người đặt (owner).
     */
    @Transactional
    public String updateNickname(String conversationId, String userId, String nickname) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của hội thoại này"));

        String trimmed = (nickname != null) ? nickname.trim() : null;
        member.setNickname((trimmed != null && !trimmed.isEmpty()) ? trimmed : null);
        conversationMemberRepository.save(member);

        log.info("User {} updated nickname in conversation {} to: {}", userId, conversationId, trimmed);
        return member.getNickname();
    }

    // ==================== CONVERSATION TAG ====================

    public String updateConversationTag(String conversationId, String userId, String tag) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của hội thoại này"));

        String trimmed = (tag != null) ? tag.trim() : null;
        member.setConversationTag((trimmed != null && !trimmed.isEmpty()) ? trimmed : null);
        conversationMemberRepository.save(member);

        log.info("User {} updated conversation tag in {} to: {}", userId, conversationId, trimmed);
        return member.getConversationTag();
    }

    // ==================== READ STATUS ====================

    /**
     * Get read status (lastReadMessageId) for all members in a conversation.
     * Lấy trạng thái đọc tin nhắn của tất cả thành viên.
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getReadStatus(String conversationId, String userId) {
        // Verify user is a member
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của hội thoại này"));

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        List<java.util.Map<String, Object>> result = new ArrayList<>();

        for (ConversationMember m : members) {
            if (m.getUserId().equals(userId))
                continue; // Skip self
            if (m.getLastReadMessageId() == null)
                continue; // Not read anything yet

            iuh.fit.entity.UserDetail detail = userDetailRepository.findByUserId(m.getUserId()).orElse(null);

            java.util.Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("userId", m.getUserId());
            entry.put("messageId", m.getLastReadMessageId());
            entry.put("lastReadAt", m.getLastReadAt() != null ? m.getLastReadAt().toString() : null);
            entry.put("displayName", detail != null ? detail.getDisplayName() : "Unknown");
            entry.put("avatarUrl", detail != null ? detail.getAvatarUrl() : null);
            result.add(entry);
        }
        return result;
    }

    /**
     * Mark all messages in a conversation as read for a user.
     * Đặt lastReadAt = now và xóa cờ isMarkedUnread.
     */
    @Transactional
    public void markAsRead(String conversationId, String userId) {
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .ifPresent(member -> {
                    member.setLastReadAt(LocalDateTime.now());
                    member.setIsMarkedUnread(false);
                    conversationMemberRepository.save(member);
                });
    }

    private ConversationPermission getPermissions(String conversationId) {
        return conversationPermissionRepository.findByConversationId(conversationId).orElse(null);
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

    // ==================== DISSOLVE GROUP ====================

    /**
     * Dissolve (disband) a group conversation. Admin only.
     * Giải tán nhóm — chỉ Admin mới có quyền. Xóa tất cả thành viên và đánh dấu
     * nhóm đã xóa.
     */
    @Transactional
    public void dissolveGroup(String conversationId, String requesterId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new InvalidInputException(ErrorCode.NOT_GROUP_CONVERSATION);
        }

        ConversationMember requester = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        if (requester.getRole() != MemberRole.ADMIN) {
            throw new ForbiddenException(ErrorCode.NOT_GROUP_ADMIN);
        }

        // Broadcast system message before dissolving
        broadcastSystemMessage(conversationId, "Nhóm đã bị giải tán bởi Trưởng nhóm");

        // Notify all members via group-events
        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        for (ConversationMember m : allMembers) {
            HashMap<String, Object> event = new HashMap<>();
            event.put("type", "DISSOLVED");
            event.put("conversationId", conversationId);
            event.put("conversationName", conv.getConversationName());
            messagingTemplate.convertAndSend("/topic/group-events/" + m.getUserId(), event);
        }

        // Delete all members
        conversationMemberRepository.deleteAll(allMembers);

        // Mark conversation as deleted
        conv.setIsDeleted(true);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("Group {} dissolved by admin {}", conversationId, requesterId);
    }

    // ==================== MESSAGE REQUEST (STRANGER) MANAGEMENT
    // ====================

    /**
     * Return all PENDING (message request) conversations where currentUser is the
     * receiver.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getPendingMessageRequests(String userId) {
        List<ConversationMember> memberships = conversationMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> conversationRepository.findById(m.getConversationId()).orElse(null))
                .filter(conv -> conv != null
                        && conv.getConversationStatus() == ConversationStatus.PENDING
                        && conv.getConversationType() == ConversationType.PRIVATE)
                .filter(conv -> {
                    // currentUser must be the receiver (not the first sender)
                    // We determine this: the stranger sent the first message, so the conv was
                    // created by them
                    // A simple heuristic: if the other participant is the one who opened the
                    // conversation
                    // In practice: just return all PENDING where user is a member — FE can filter
                    // sender vs receiver
                    return true;
                })
                .map(conv -> {
                    List<ConversationMember> members = conversationMemberRepository
                            .findByConversationId(conv.getConversationId());
                    return conversationMapper.toResponse(conv, members);
                })
                .collect(Collectors.toList());
    }

    /** Accept a message request: set conversation to NORMAL. */
    @Transactional
    public ConversationResponse acceptMessageRequest(String conversationId, String currentUserId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của cuộc hội thoại này"));

        if (conv.getConversationStatus() != ConversationStatus.PENDING) {
            throw new RuntimeException("Cuộc hội thoại này không phải tin nhắn chờ");
        }

        conv.setConversationStatus(ConversationStatus.NORMAL);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        // Notify the sender that request was accepted
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        ConversationResponse response = conversationMapper.toResponse(conv, members);
        for (ConversationMember m : members) {
            messagingTemplate.convertAndSend("/topic/conversation-events/" + m.getUserId(), response);
        }

        log.info("Message request accepted in conversation {} by user {}", conversationId, currentUserId);
        return response;
    }

    /** Block a message request: mark BLOCKED and update friendship as BLOCKED. */
    @Transactional
    public void blockMessageRequest(String conversationId, String currentUserId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của cuộc hội thoại này"));

        conv.setConversationStatus(ConversationStatus.BLOCKED);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        // Find the other participant and block them in friendship
        if (conv.getParticipants() != null) {
            String senderId = conv.getParticipants().stream()
                    .filter(id -> !id.equals(currentUserId)).findFirst().orElse(null);
            if (senderId != null) {
                friendshipRepository.findByRequesterIdAndReceiverId(currentUserId, senderId)
                        .ifPresentOrElse(friendship -> {
                            friendship.setStatus(FriendshipStatus.BLOCKED);
                            friendship.setRequesterId(currentUserId);
                            friendship.setReceiverId(senderId);
                            friendship.setUpdatedAt(LocalDateTime.now());
                            friendshipRepository.save(friendship);
                        }, () -> {
                            Friendship newBlock = Friendship.builder()
                                    .requesterId(currentUserId)
                                    .receiverId(senderId)
                                    .status(FriendshipStatus.BLOCKED)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            friendshipRepository.save(newBlock);
                        });
            }
        }

        log.info("Message request blocked in conversation {} by user {}", conversationId, currentUserId);
    }

    /**
     * Decline a message request: soft-delete the conversation for the current user.
     */
    @Transactional
    public void declineMessageRequest(String conversationId, String currentUserId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của cuộc hội thoại này"));

        if (conv.getConversationStatus() != ConversationStatus.PENDING) {
            throw new RuntimeException("Cuộc hội thoại này không phải tin nhắn chờ");
        }

        // Soft-delete: mark conversation as deleted for this user (reuse
        // isPinned-per-member pattern)
        conv.setIsDeleted(true);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("Message request declined in conversation {} by user {}", conversationId, currentUserId);
    }

    // ==================== MUTE CONVERSATION ====================

    @Transactional
    public java.util.Map<String, Object> muteConversation(String conversationId, String userId, String duration) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        LocalDateTime mutedUntil;
        switch (duration) {
            case "1h":
                mutedUntil = LocalDateTime.now().plusHours(1);
                break;
            case "4h":
                mutedUntil = LocalDateTime.now().plusHours(4);
                break;
            case "until_8am":
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime next8am = now.toLocalDate().atTime(8, 0);
                if (now.isAfter(next8am)) {
                    next8am = next8am.plusDays(1);
                }
                mutedUntil = next8am;
                break;
            case "forever":
                mutedUntil = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
                break;
            case "off":
                mutedUntil = null;
                break;
            default:
                throw new InvalidInputException(ErrorCode.INVALID_MUTE_DURATION);
        }

        member.setMutedUntil(mutedUntil);
        conversationMemberRepository.save(member);

        log.info("User {} {} conversation {}", userId, mutedUntil != null ? "muted" : "unmuted", conversationId);

        HashMap<String, Object> event = new HashMap<>();
        event.put("type", "MUTED");
        event.put("conversationId", conversationId);
        event.put("mutedUntil", mutedUntil != null ? mutedUntil.toString() : null);
        messagingTemplate.convertAndSend("/topic/conversation-events/" + userId, event);

        return event;
    }

    // ==================== MARK AS UNREAD ====================

    @Transactional
    public java.util.Map<String, Object> toggleMarkUnread(String conversationId, String userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        boolean newMarked = !Boolean.TRUE.equals(member.getIsMarkedUnread());
        member.setIsMarkedUnread(newMarked);
        conversationMemberRepository.save(member);

        log.info("User {} {} conversation {} as unread", userId, newMarked ? "marked" : "unmarked", conversationId);

        HashMap<String, Object> event = new HashMap<>();
        event.put("type", "MARK_UNREAD");
        event.put("conversationId", conversationId);
        event.put("isMarkedUnread", newMarked);
        messagingTemplate.convertAndSend("/topic/conversation-events/" + userId, event);

        return event;
    }

    // ==================== AUTO-DELETE MESSAGES ====================

    @Transactional
    public java.util.Map<String, Object> updateAutoDeleteDuration(String conversationId, String userId,
            String duration) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        if (!List.of("off", "1d", "7d", "30d").contains(duration)) {
            throw new InvalidInputException(ErrorCode.INVALID_AUTO_DELETE_DURATION);
        }

        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        // For group conversations, only ADMIN/DEPUTY can change auto-delete
        if (conv.getConversationType() == ConversationType.GROUP) {
            if (member.getRole() != MemberRole.ADMIN && member.getRole() != MemberRole.DEPUTY) {
                throw new ForbiddenException(ErrorCode.NOT_GROUP_ADMIN);
            }
        }

        String oldDuration = conv.getAutoDeleteDuration();
        conv.setAutoDeleteDuration("off".equals(duration) ? null : duration);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("User {} set auto-delete to {} for conversation {}", userId, duration, conversationId);

        // Broadcast system message about the change
        iuh.fit.entity.UserDetail userDetail = userDetailRepository.findByUserId(userId).orElse(null);
        String displayName = userDetail != null ? userDetail.getDisplayName() : "Ai đó";
        if ("off".equals(duration)) {
            broadcastSystemMessage(conversationId, displayName + " đã tắt tin nhắn tự xóa");
        } else {
            String label = duration.equals("1d") ? "1 ngày" : duration.equals("7d") ? "7 ngày" : "30 ngày";
            broadcastSystemMessage(conversationId, displayName + " đã bật tin nhắn tự xóa sau " + label);
        }

        // Notify all members about auto-delete change
        HashMap<String, Object> event = new HashMap<>();
        event.put("type", "AUTO_DELETE_UPDATED");
        event.put("conversationId", conversationId);
        event.put("autoDeleteDuration", conv.getAutoDeleteDuration());
        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        for (ConversationMember m : allMembers) {
            messagingTemplate.convertAndSend("/topic/conversation-events/" + m.getUserId(), event);
        }

        return event;
    }

    // ==================== HIDE / UNHIDE CONVERSATION ====================

    /**
     * Get all hidden conversations for a user.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getHiddenConversations(String userId) {
        List<ConversationMember> memberships = conversationMemberRepository.findByUserId(userId);
        return memberships.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsHidden()))
                .map(m -> {
                    Conversations conv = conversationRepository.findById(m.getConversationId()).orElse(null);
                    if (conv == null || Boolean.TRUE.equals(conv.getIsDeleted()))
                        return null;
                    enrichWithLastMessage(conv);
                    List<ConversationMember> members = conversationMemberRepository
                            .findByConversationId(conv.getConversationId());
                    return conversationMapper.toResponse(conv, members, getPermissions(conv.getConversationId()), userId);
                })
                .filter(resp -> resp != null)
                .collect(Collectors.toList());
    }

    /**
     * Unhide a previously hidden conversation for a user.
     */
    @Transactional
    public java.util.Map<String, Object> unhideConversation(String conversationId, String userId, String rawPin) {
        iuh.fit.entity.UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (userAuth.getPinCode() == null)
            throw new RuntimeException("Bạn chưa thiết lập mã PIN.");
        if (!passwordEncoder.matches(rawPin, userAuth.getPinCode()))
            throw new RuntimeException("Mã PIN không chính xác");

        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NOT_CONVERSATION_MEMBER));

        member.setIsHidden(false);
        conversationMemberRepository.save(member);

        log.info("User {} unhid conversation {}", userId, conversationId);

        HashMap<String, Object> event = new HashMap<>();
        event.put("type", "CONVERSATION_UNHIDDEN");
        event.put("conversationId", conversationId);
        messagingTemplate.convertAndSend("/topic/conversation-events/" + userId, event);

        return event;
    }

    // ==================== PIN-PROTECTED HIDE ====================

    /**
     * Hide a conversation only after verifying the user's PIN.
     */
    @Transactional
    public java.util.Map<String, Object> hideConversationWithPin(String conversationId, String userId, String rawPin) {
        iuh.fit.entity.UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (userAuth.getPinCode() == null) {
            throw new RuntimeException("Bạn chưa thiết lập mã PIN. Vui lòng thiết lập PIN trước.");
        }
        if (!passwordEncoder.matches(rawPin, userAuth.getPinCode())) {
            throw new RuntimeException("Mã PIN không chính xác");
        }

        return softDeleteConversation(conversationId, userId);
    }

    /**
     * Search hidden conversations by keyword (case-insensitive name match).
     * Returns hidden results only if PIN is correct, else returns empty list.
     */
    public List<ConversationResponse> searchHiddenConversations(String userId, String keyword, String rawPin) {
        iuh.fit.entity.UserAuth userAuth = userAuthRepository.findById(userId).orElse(null);
        if (userAuth == null || userAuth.getPinCode() == null)
            return List.of();
        if (!passwordEncoder.matches(rawPin, userAuth.getPinCode()))
            return List.of();

        String lowerKeyword = (keyword == null) ? "" : keyword.trim().toLowerCase();

        return getHiddenConversations(userId).stream()
                .filter(conv -> {
                    if (lowerKeyword.isEmpty())
                        return true;
                    String name = conv.getConversationName() != null ? conv.getConversationName().toLowerCase() : "";
                    String lastMsg = conv.getLastMessageContent() != null ? conv.getLastMessageContent().toLowerCase()
                            : "";
                    return name.contains(lowerKeyword) || lastMsg.contains(lowerKeyword);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationResponse joinGroup(String conversationId, String userId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conv.getConversationType() != ConversationType.GROUP) {
            throw new InvalidInputException(ErrorCode.INVALID_INPUT, "Chỉ có thể tham gia nhóm chat qua link");
        }

        // Check if already a member
        Optional<ConversationMember> existing = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId);
        if (existing.isPresent()) {
            // Already a member, just return the conversation
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            return conversationMapper.toResponse(conv, members, getPermissions(conversationId), userId);
        }

        // Create new member
        ConversationMember newMember = ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
        conversationMemberRepository.save(newMember);

        // Broadcast system message
        iuh.fit.entity.UserDetail detail = userDetailRepository.findByUserId(userId).orElse(null);
        String name = detail != null ? detail.getDisplayName() : "Ai đó";
        broadcastSystemMessage(conversationId, name + " đã tham gia nhóm qua link");

        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationId(conversationId);
        enrichWithLastMessage(conv);
        ConversationResponse response = conversationMapper.toResponse(conv, allMembers, getPermissions(conversationId), userId);
        response.setType("CREATED"); // Notify the joining user

        // Notify other members
        for (ConversationMember m : allMembers) {
            if (!m.getUserId().equals(userId)) {
                messagingTemplate.convertAndSend("/topic/group-events/" + m.getUserId(), response);
            }
        }

        return response;
    }
    public java.util.Map<String, Object> getGroupPreview(String conversationId) {
        Conversations conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new iuh.fit.exception.ResourceNotFoundException(iuh.fit.exception.ErrorCode.CONVERSATION_NOT_FOUND));

        if (conv.getConversationType() != iuh.fit.enums.ConversationType.GROUP) {
            throw new iuh.fit.exception.InvalidInputException(iuh.fit.exception.ErrorCode.INVALID_INPUT, "Hội thoại không phải là nhóm");
        }

        List<iuh.fit.entity.ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        
        java.util.Map<String, Object> preview = new java.util.HashMap<>();
        preview.put("conversationId", conv.getConversationId());
        preview.put("name", conv.getConversationName());
        preview.put("avatar", conv.getAvatarUrl());
        preview.put("memberCount", members.size());
        
        // Get some member avatars for the preview
        List<String> memberAvatars = members.stream()
                .map(m -> {
                    iuh.fit.entity.UserDetail detail = userDetailRepository.findByUserId(m.getUserId()).orElse(null);
                    return detail != null ? detail.getAvatarUrl() : null;
                })
                .filter(java.util.Objects::nonNull)
                .limit(3)
                .collect(java.util.stream.Collectors.toList());
        preview.put("memberAvatars", memberAvatars);
        
        String userId = JwtUtils.getCurrentUserId();
        if (userId != null) {
            boolean isMember = members.stream().anyMatch(m -> m.getUserId().equals(userId));
            preview.put("isMember", isMember);
        } else {
            preview.put("isMember", false);
        }

        return preview;
    }
}
