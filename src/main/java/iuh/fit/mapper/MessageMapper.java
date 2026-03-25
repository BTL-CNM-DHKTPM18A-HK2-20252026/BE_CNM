package iuh.fit.mapper;

import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.Message;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageMapper {
    
    private final UserDetailRepository userDetailRepository;
    
    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }
        
        UserDetail detail = userDetailRepository.findByUserId(message.getSenderId()).orElse(null);
        
        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(detail != null ? detail.getDisplayName() : "Unknown")
                .senderAvatarUrl(detail != null ? detail.getAvatarUrl() : null)
                .content(message.getContent())
                .messageType(message.getMessageType() != null ? message.getMessageType().toString() : null)
                .replyToMessageId(message.getReplyToMessageId())
                .isEdited(message.getIsEdited())
                .isDeleted(message.getIsDeleted())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
