package iuh.fit.mapper;

import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.ImageUploadMetadata;
import iuh.fit.entity.Message;
import iuh.fit.entity.MessageAttachment;
import iuh.fit.entity.UserDetail;
import iuh.fit.enums.MessageType;
import iuh.fit.repository.ImageUploadMetadataRepository;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.MessageReactionRepository;
import iuh.fit.dto.response.message.MessageReactionDto;
import java.util.List;
import java.util.Optional;
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
        private final ImageUploadMetadataRepository imageUploadMetadataRepository;
        private final MessageAttachmentRepository messageAttachmentRepository;

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

                String resolvedS3Url = null;
                Integer resolvedWidth = null;
                Integer resolvedHeight = null;

                if (message.getMessageType() == MessageType.IMAGE) {
                        resolvedS3Url = message.getContent();
                        resolvedWidth = 0;
                        resolvedHeight = 0;

                        Optional<ImageUploadMetadata> metadataOpt = imageUploadMetadataRepository
                                        .findByS3Url(resolvedS3Url);
                        if (metadataOpt.isEmpty()) {
                                String s3Key = extractS3Key(resolvedS3Url);
                                if (s3Key != null) {
                                        metadataOpt = imageUploadMetadataRepository.findByS3Key(s3Key);
                                }
                        }

                        if (metadataOpt.isPresent()) {
                                ImageUploadMetadata metadata = metadataOpt.get();
                                resolvedWidth = metadata.getWidth();
                                resolvedHeight = metadata.getHeight();
                        }
                }

                // Resolve attachments for IMAGE_GROUP
                List<MessageResponse.AttachmentDto> attachmentDtos = null;
                if (message.getMessageType() == MessageType.IMAGE_GROUP) {
                        List<MessageAttachment> attachments = messageAttachmentRepository
                                        .findByMessageId(message.getMessageId());
                        if (attachments != null && !attachments.isEmpty()) {
                                attachmentDtos = attachments.stream()
                                                .map(att -> MessageResponse.AttachmentDto.builder()
                                                                .url(att.getUrl())
                                                                .fileName(att.getFileName())
                                                                .fileSize(att.getFileSize())
                                                                .thumbnailUrl(att.getThumbnailUrl())
                                                                .build())
                                                .collect(Collectors.toList());
                        }
                }

                return MessageResponse.builder()
                                .messageId(message.getMessageId())
                                .conversationId(message.getConversationId())
                                .senderId(message.getSenderId())
                                .senderName(senderName)
                                .senderAvatarUrl(detail != null ? detail.getAvatarUrl() : null)
                                .role(message.getRole() != null ? message.getRole().name() : null)
                                .content(displayContent)
                                .messageType(message.getMessageType() != null ? message.getMessageType().toString()
                                                : null)
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
                                .s3Url(resolvedS3Url)
                                .width(resolvedWidth)
                                .height(resolvedHeight)
                                .caption(message.getCaption())
                                .forwardedFromMessageId(message.getForwardedFromMessageId())
                                .forwardedFromSenderName(forwardedFromSenderName)
                                .reactions(reactionDtos)
                                .mentions(message.getMentions())
                                .attachments(attachmentDtos)
                                .build();
        }

        private String extractS3Key(String s3Url) {
                if (s3Url == null || s3Url.isBlank()) {
                        return null;
                }

                int marker = s3Url.indexOf(".amazonaws.com/");
                if (marker == -1) {
                        return null;
                }

                String key = s3Url.substring(marker + ".amazonaws.com/".length());
                int queryIdx = key.indexOf('?');
                if (queryIdx >= 0) {
                        key = key.substring(0, queryIdx);
                }

                return key.isBlank() ? null : key;
        }
}
