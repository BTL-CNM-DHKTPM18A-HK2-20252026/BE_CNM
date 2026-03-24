package iuh.fit.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Message;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    
    // Find messages by conversation
    Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
    
    // Find messages older than a specific date for Cursor-based pagination
    Page<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
        String conversationId, LocalDateTime createdAt, Pageable pageable);
    
    // Find messages by sender
    List<Message> findBySenderId(String senderId);
    
    // Count unread messages in conversation for a user
    long countByConversationIdAndIsDeletedFalseAndCreatedAtGreaterThan(
        String conversationId, LocalDateTime lastSeenAt);
}
