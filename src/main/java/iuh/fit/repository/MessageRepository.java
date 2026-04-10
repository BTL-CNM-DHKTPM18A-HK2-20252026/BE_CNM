package iuh.fit.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Message;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

        // Find messages by conversation
        Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

        // Find media messages in a conversation
        List<Message> findByConversationIdAndMessageTypeInOrderByCreatedAtDesc(
                        String conversationId, Collection<String> messageTypes);

        // Find messages older than a specific date for Cursor-based pagination
        Page<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        String conversationId, LocalDateTime createdAt, Pageable pageable); // Changed back to
                                                                                            // LocalDateTime

        // Find messages newer than a specific date (for "around message" pagination)
        List<Message> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(
                        String conversationId, LocalDateTime createdAt, Pageable pageable);

        // Search messages by content (case-insensitive regex) in a conversation
        @Query("{ 'conversationId': ?0, 'content': { $regex: ?1, $options: 'i' }, 'isDeleted': { $ne: true } }")
        Page<Message> searchByConversationIdAndContent(String conversationId, String query, Pageable pageable);

        // Search messages by content across multiple conversations (MongoDB fallback)
        @Query("{ 'conversationId': { $in: ?0 }, 'content': { $regex: ?1, $options: 'i' }, 'isDeleted': { $ne: true } }")
        Page<Message> searchByConversationIdsAndContent(List<String> conversationIds, String query, Pageable pageable);

        // Find messages by sender
        List<Message> findBySenderId(String senderId);

        // Find latest messages sent by a user
        Page<Message> findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable pageable);

        // Count visible messages in a conversation
        long countByConversationIdAndIsDeletedFalse(String conversationId);

        // Count unread messages in conversation for a user
        long countByConversationIdAndIsDeletedFalseAndCreatedAtGreaterThan(
                        String conversationId, LocalDateTime lastSeenAt); // Changed back to LocalDateTime

        // Count unread messages after lastReadAt, excluding messages sent by the user
        // themselves
        long countByConversationIdAndIsDeletedFalseAndCreatedAtGreaterThanAndSenderIdNot(
                        String conversationId, LocalDateTime afterDate, String excludeSenderId);

        // Count all messages in a conversation, excluding messages sent by the user
        // (for never-read case)
        long countByConversationIdAndIsDeletedFalseAndSenderIdNot(
                        String conversationId, String excludeSenderId);

        // Delete old messages for auto-delete feature
        long deleteByConversationIdAndCreatedAtBefore(String conversationId, LocalDateTime cutoff);

        // Find all messages in a conversation (no pagination — used for bulk
        // operations)
        List<Message> findByConversationId(String conversationId);

        // Hard-delete all messages in a conversation (used by clearConversationAll)
        void deleteByConversationId(String conversationId);

        // Find AI-generated messages in conversations after a certain time
        @Query("{ 'conversationId': { $in: ?0 }, 'aiGenerated': true, 'createdAt': { $gte: ?1 } }")
        List<Message> findAiMessagesByConversationIdsAfter(List<String> conversationIds, LocalDateTime after);
}
