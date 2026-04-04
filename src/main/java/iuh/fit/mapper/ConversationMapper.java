package iuh.fit.mapper;

import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
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

        public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members) {
                return toResponse(conversation, members, null);
        }

        public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members,
                        String currentUserId) {
                List<ConversationResponse.MemberInfo> memberInfos = members.stream()
                                .map(this::mapMember)
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
                                .build();
        }

        private ConversationResponse.MemberInfo mapMember(ConversationMember member) {
                UserDetail detail = userDetailRepository.findByUserId(member.getUserId()).orElse(null);
                return ConversationResponse.MemberInfo.builder()
                                .userId(member.getUserId())
                                .displayName(detail != null ? detail.getDisplayName() : "Unknown")
                                .avatarUrl(detail != null ? detail.getAvatarUrl() : null)
                                .role(member.getRole() != null ? member.getRole().name() : null)
                                .nickname(member.getNickname())
                                .build();
        }

        public ConversationResponse.MemberInfo toMemberInfo(ConversationMember member) {
                return mapMember(member);
        }
}
