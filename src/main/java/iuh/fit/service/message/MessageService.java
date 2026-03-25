package iuh.fit.service.message;

import iuh.fit.dto.request.message.SendMessageRequest;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Message;
import iuh.fit.entity.MessageAttachment;
import iuh.fit.enums.MessageType;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessageSendingOperations;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageMapper messageMapper;
    private final SimpMessageSendingOperations messagingTemplate;
    
    @Transactional
    public MessageResponse sendMessage(String senderId, SendMessageRequest request) {
        MessageType type = MessageType.TEXT;
        if (request.getMessageType() != null) {
            try {
                type = MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                type = MessageType.TEXT;
            }
        }

        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(request.getConversationId())
                .senderId(senderId)
                .content(request.getContent())
                .messageType(type)
                .replyToMessageId(request.getReplyToMessageId())
                .isEdited(false)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        message = messageRepository.save(message);
        log.info("Message sent: {}", message.getMessageId());

        // Save attachment if metadata is provided
        if (request.getFileName() != null && request.getFileSize() != null) {
            MessageAttachment attachment = MessageAttachment.builder()
                    .messageId(message.getMessageId())
                    .fileName(request.getFileName())
                    .fileSize(request.getFileSize())
                    .url(message.getContent())
                    .thumbnailUrl(request.getMessageType() != null && (request.getMessageType().equalsIgnoreCase("IMAGE") || request.getMessageType().equalsIgnoreCase("VIDEO")) ? message.getContent() : null)
                    .build();
            messageAttachmentRepository.save(attachment);
            log.info("Saved attachment for message: {}", message.getMessageId());
        }

        // Update conversation last message denormalized fields
        Conversations conv = conversationRepository.findById(request.getConversationId()).orElse(null);
        if (conv != null) {
            String snippet = message.getContent();
            if (message.getMessageType() == MessageType.IMAGE) snippet = "[Hình ảnh]";
            else if (message.getMessageType() == MessageType.VIDEO) snippet = "[Video]";
            else if (message.getMessageType() == MessageType.MEDIA) snippet = "[File]";
            
            conv.setLastMessageId(message.getMessageId());
            conv.setLastMessageContent(snippet);
            conv.setLastMessageTime(message.getCreatedAt());
            conv.setUpdatedAt(message.getCreatedAt());
            conversationRepository.save(conv);
            log.info("Updated conversation {} last message snippet to: {}", conv.getConversationId(), snippet);
        }
        
        MessageResponse response = messageMapper.toResponse(message);
        messagingTemplate.convertAndSend("/topic/chat/" + request.getConversationId(), response);
        
        return response;
    }
    
    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(messageMapper::toResponse);
    }
    
    public Page<MessageResponse> getMessagesBefore(String conversationId, String beforeId, int size) {
        Pageable pageable = PageRequest.of(0, size);
        
        // Find the cursor message first
        Message beforeMsg = messageRepository.findById(beforeId).orElse(null);
        if (beforeMsg == null) {
            return Page.empty();
        }
        
        return messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            conversationId, beforeMsg.getCreatedAt(), pageable)
            .map(messageMapper::toResponse);
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
        
        return messageMapper.toResponse(message);
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
}
