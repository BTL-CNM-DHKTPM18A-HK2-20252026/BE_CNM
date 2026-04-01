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

    private static final String AI_SENDER_ID = "FRUVIA_AI_ASSISTANT";
    private static final String AI_SENDER_NAME = "Fruvia AI";

    private final UserDetailRepository userDetailRepository;
    private final MessageReactionRepository messageReactionRepository;

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }

        UserDetail detail = userDetailRepository.findByUserId(message.getSenderId()).orElse(null);
        String senderName = detail != null ? detail.getDisplayName()
                : (AI_SENDER_ID.equals(message.getSenderId()) ? AI_SENDER_NAME : "Unknown");

        List<MessageReactionDto> reactionDtos = messageReactionRepository
                .findReactionsWithUserByMessageId(message.getMessageId());

        // If recalled, hide the actual content
        String displayContent = Boolean.TRUE.equals(message.getIsRecalled())
                ? null
                : message.getContent();

        // Resolve forward sender name
        String forwardedFromSenderName = null;
        if (message.getForwardedFromSenderId() != null) {
            forwardedFromSenderName = userDetailRepository.findByUserId(message.getForwardedFromSenderId())
                    .map(UserDetail::getDisplayName).orElse("Unknown");
        }

        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(senderName)
                .senderAvatarUrl(detail != null ? detail.getAvatarUrl() : null)
                .role(message.getRole() != null ? message.getRole().name() : null)
                .content(displayContent)
                .messageType(message.getMessageType() != null ? message.getMessageType().toString() : null)
                .replyToMessageId(message.getReplyToMessageId())
                .isEdited(message.getIsEdited())
                .isDeleted(message.getIsDeleted())
                .isRecalled(message.getIsRecalled())
                .aiGenerated(message.getAiGenerated())
                .aiModel(message.getAiModel())
                .aiStatus(message.getAiStatus() != null ? message.getAiStatus().name() : null)
                .promptTokens(message.getPromptTokens())
                .completionTokens(message.getCompletionTokens())
                .totalTokens(message.getTotalTokens())
                .aiLatencyMs(message.getAiLatencyMs())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .linkTitle(message.getLinkTitle())
                .linkThumbnail(message.getLinkThumbnail())
                .voiceDuration(message.getVoiceDuration())
                .videoDuration(message.getVideoDuration())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .forwardedFromMessageId(message.getForwardedFromMessageId())
                .forwardedFromSenderName(forwardedFromSenderName)
                .reactions(reactionDtos)
                .build();
    }
}
