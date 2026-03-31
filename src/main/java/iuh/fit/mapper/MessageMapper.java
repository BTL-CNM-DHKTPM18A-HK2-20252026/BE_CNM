package iuh.fit.mapper;

import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.Message;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.MessageReactionRepository;
import iuh.fit.dto.response.message.MessageReactionDto;
import iuh.fit.entity.MessageReaction;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final UserDetailRepository userDetailRepository;
    private final MessageReactionRepository messageReactionRepository;

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }

        UserDetail detail = userDetailRepository.findByUserId(message.getSenderId()).orElse(null);

        List<MessageReactionDto> reactionDtos = messageReactionRepository
                .findReactionsWithUserByMessageId(message.getMessageId());

        // If recalled, hide the actual content
        String displayContent = Boolean.TRUE.equals(message.getIsRecalled())
                ? null
                : message.getContent();

        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(detail != null ? detail.getDisplayName() : "Unknown")
                .senderAvatarUrl(detail != null ? detail.getAvatarUrl() : null)
                .content(displayContent)
                .messageType(message.getMessageType() != null ? message.getMessageType().toString() : null)
                .replyToMessageId(message.getReplyToMessageId())
                .isEdited(message.getIsEdited())
                .isDeleted(message.getIsDeleted())
                .isRecalled(message.getIsRecalled())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .linkTitle(message.getLinkTitle())
                .linkThumbnail(message.getLinkThumbnail())
                .voiceDuration(message.getVoiceDuration())
                .reactions(reactionDtos)
                .build();
    }
}
