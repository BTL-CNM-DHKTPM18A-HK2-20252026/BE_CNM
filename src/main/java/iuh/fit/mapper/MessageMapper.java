package iuh.fit.mapper;

import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {
    
    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }
        
        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
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
