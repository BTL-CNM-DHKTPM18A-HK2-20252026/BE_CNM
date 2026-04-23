package iuh.fit.mapper;

import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.ConversationPermission;
import iuh.fit.entity.Conversations;
import iuh.fit.dto.response.conversation.ConversationPermissionResponse;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConversationMapper {

        private final UserDetailRepository userDetailRepository;
        private final MessageRepository messageRepository;
        private final iuh.fit.repository.FriendshipRepository friendshipRepository;

        public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members) {
                return toResponse(conversation, members, (ConversationPermission) null, null);
        }

        public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members,
                        String currentUserId) {
                return toResponse(conversation, members, (ConversationPermission) null, currentUserId);
        }

        public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members,
                        ConversationPermission permission) {
                return toResponse(conversation, members, permission, null);
        }

        public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members,
                        ConversationPermission permission, String currentUserId) {
                List<ConversationResponse.MemberInfo> memberInfos = members.stream()
                                .map(m -> mapMember(m, currentUserId))
                                .collect(Collectors.toList());

                Boolean isPinned = null;
                LocalDateTime pinnedAt = null;
                if (currentUserId != null) {
                        Optional<ConversationMember> currentMember = members.stream()
                                        .filter(m -> m.getUserId().equals(currentUserId))
                                        .findFirst();
                        isPinned = currentMember
                                        .map(m -> Boolean.TRUE.equals(m.getIsPinned()))
                                        .orElse(false);
                        pinnedAt = currentMember
                                        .map(ConversationMember::getPinnedAt)
                                        .orElse(null);
                }

                String conversationTag = null;
                LocalDateTime mutedUntil = null;
                Boolean isMarkedUnread = null;
                Integer unreadCount = 0;
                if (currentUserId != null) {
                        Optional<ConversationMember> currentMemberForExtras = members.stream()
                                        .filter(m -> m.getUserId().equals(currentUserId))
                                        .findFirst();
                        conversationTag = currentMemberForExtras.map(ConversationMember::getConversationTag)
                                        .orElse(null);
                        mutedUntil = currentMemberForExtras.map(ConversationMember::getMutedUntil).orElse(null);
                        isMarkedUnread = currentMemberForExtras
                                        .map(m -> Boolean.TRUE.equals(m.getIsMarkedUnread()))
                                        .orElse(false);

                        // Count unread messages: messages after lastReadAt that were not sent by this
                        // user
                        LocalDateTime lastReadAt = currentMemberForExtras
                                        .map(ConversationMember::getLastReadAt)
                                        .orElse(null);
                        String convId = conversation.getConversationId();
                        long count;
                        if (lastReadAt == null) {
                                // User has never opened this conversation — count all messages except their own
                                count = messageRepository.countByConversationIdAndIsDeletedFalseAndSenderIdNot(
                                                convId, currentUserId);
                        } else {
                                // Count messages sent after the user last read, excluding their own
                                count = messageRepository
                                                .countByConversationIdAndIsDeletedFalseAndCreatedAtGreaterThanAndSenderIdNot(
                                                                convId, lastReadAt, currentUserId);
                        }
                        unreadCount = (int) Math.min(count, Integer.MAX_VALUE);
                }

                return ConversationResponse.builder()
                                .conversationId(conversation.getConversationId())
                                .conversationType(conversation.getConversationType() != null
                                                ? conversation.getConversationType().name()
                                                : "UNKNOWN")
                                .conversationName(conversation.getConversationName())
                                .conversationAvatarUrl(conversation.getAvatarUrl())
                                .lastMessageContent(conversation.getLastMessageContent())
                                .lastMessageTime(conversation.getLastMessageTime())
                                .isPinned(isPinned)
                                .pinnedAt(pinnedAt)
                                .members(memberInfos)
                                .createdAt(conversation.getCreatedAt())
                                .conversationStatus(conversation.getConversationStatus() != null
                                                ? conversation.getConversationStatus().name()
                                                : "NORMAL")
                                .conversationTag(conversationTag)
                                .groupDescription(conversation.getGroupDescription())
                                .mutedUntil(mutedUntil)
                                .isMarkedUnread(isMarkedUnread)
                                .unreadCount(unreadCount)
                                .autoDeleteDuration(conversation.getAutoDeleteDuration())
                                .invitationLink(conversation.getInvitationLink() != null ? conversation.getInvitationLink() : "fruvi.chat/" + conversation.getConversationId())
                                .permissions(toPermissionResponse(permission))
                                .build();
        }

        public ConversationPermissionResponse toPermissionResponse(ConversationPermission permission) {
                if (permission == null)
                        return null;
                return ConversationPermissionResponse.builder()
                                .canEditInfo(permission.getCanEditInfo())
                                .canPinMessages(permission.getCanPinMessages())
                                .canCreateNotes(permission.getCanCreateNotes())
                                .canCreatePolls(permission.getCanCreatePolls())
                                .canSendMessages(permission.getCanSendMessages())
                                .isMemberApprovalRequired(permission.getIsMemberApprovalRequired())
                                .isHighlightAdminMessages(permission.getIsHighlightAdminMessages())
                                .canNewMembersReadRecentMessages(permission.getCanNewMembersReadRecentMessages())
                                .build();
        }

        private ConversationResponse.MemberInfo mapMember(ConversationMember member, String currentUserId) {
                UserDetail detail = userDetailRepository.findByUserId(member.getUserId()).orElse(null);
                
                Boolean isFriend = null;
                if (currentUserId != null && !currentUserId.equals(member.getUserId())) {
                        isFriend = friendshipRepository.findByRequesterIdAndReceiverId(currentUserId, member.getUserId())
                                        .map(f -> f.getStatus() == iuh.fit.enums.FriendshipStatus.ACCEPTED)
                                        .orElse(false);
                }

                return ConversationResponse.MemberInfo.builder()
                                .userId(member.getUserId())
                                .displayName(detail != null ? detail.getDisplayName() : "Unknown")
                                .avatarUrl(detail != null ? detail.getAvatarUrl() : null)
                                .role(member.getRole() != null ? member.getRole().name() : null)
                                .nickname(member.getNickname())
                                .isFriend(isFriend)
                                .build();
        }

        public ConversationResponse.MemberInfo toMemberInfo(ConversationMember member, String currentUserId) {
                return mapMember(member, currentUserId);
        }
}
