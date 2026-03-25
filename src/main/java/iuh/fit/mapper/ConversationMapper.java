package iuh.fit.mapper;

import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConversationMapper {

    private final UserDetailRepository userDetailRepository;

    public ConversationResponse toResponse(Conversations conversation, List<ConversationMember> members) {
        List<ConversationResponse.MemberInfo> memberInfos = members.stream()
                .map(this::mapMember)
                .collect(Collectors.toList());

        return ConversationResponse.builder()
                .conversationId(conversation.getConversationId())
                .conversationType(conversation.getConversationType() != null ? conversation.getConversationType().name() : "UNKNOWN")
                .conversationName(conversation.getConversationName())
                .conversationAvatarUrl(conversation.getAvatarUrl())
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageTime(conversation.getLastMessageTime())
                .members(memberInfos)
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private ConversationResponse.MemberInfo mapMember(ConversationMember member) {
        UserDetail detail = userDetailRepository.findByUserId(member.getUserId()).orElse(null);
        return ConversationResponse.MemberInfo.builder()
                .userId(member.getUserId())
                .displayName(detail != null ? detail.getDisplayName() : "Unknown")
                .avatarUrl(detail != null ? detail.getAvatarUrl() : null)
                .role(member.getRole() != null ? member.getRole().name() : null)
                .build();
    }
}
