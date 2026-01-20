package iuh.fit.service.message;

import iuh.fit.dto.request.SendMessageRequest;
import iuh.fit.dto.response.MessageResponse;
import iuh.fit.entity.Message;
import iuh.fit.enums.MessageType;
import iuh.fit.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageRepository messageRepository;
    
    @Transactional
    public MessageResponse sendMessage(String senderId, SendMessageRequest request) {
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(request.getConversationId())
                .senderId(senderId)
                .content(request.getContent())
                .messageType(MessageType.TEXT)
                .replyToMessageId(request.getReplyToMessageId())
                .isEdited(false)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        message = messageRepository.save(message);
        log.info("Message sent: {}", message.getMessageId());
        
        return mapToResponse(message);
    }
    
    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(this::mapToResponse);
    }
    
    @Transactional
    public MessageResponse updateMessage(String messageId, String content, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        
        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Not authorized to edit this message");
        }
        
        message.setContent(content);
        message.setIsEdited(true);
        message.setUpdatedAt(LocalDateTime.now());
        
        message = messageRepository.save(message);
        log.info("Message updated: {}", messageId);
        
        return mapToResponse(message);
    }
    
    @Transactional
    public void deleteMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        
        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Not authorized to delete this message");
        }
        
        message.setIsDeleted(true);
        messageRepository.save(message);
        log.info("Message deleted: {}", messageId);
    }
    
    private MessageResponse mapToResponse(Message message) {
        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .messageType(message.getMessageType().toString())
                .replyToMessageId(message.getReplyToMessageId())
                .isEdited(message.getIsEdited())
                .isDeleted(message.getIsDeleted())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
