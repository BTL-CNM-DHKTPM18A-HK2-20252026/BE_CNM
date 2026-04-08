package iuh.fit.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.MessageBucket;

@Repository
public interface MessageBucketRepository extends MongoRepository<MessageBucket, String> {

    /**
     * Find the latest non-full bucket for a conversation (to append new messages).
     */
    Optional<MessageBucket> findFirstByConversationIdAndIsFullFalseOrderBySequenceNumberDesc(String conversationId);

    /**
     * Find the latest bucket for a conversation (to determine next sequence
     * number).
     */
    Optional<MessageBucket> findFirstByConversationIdOrderBySequenceNumberDesc(String conversationId);

    /**
     * Find the bucket containing a specific messageId (for sync operations).
     */
    @Query("{ 'messages.messageId': ?0 }")
    Optional<MessageBucket> findByMessagesMessageId(String messageId);

    /**
     * Delete all buckets for a conversation (used by clearConversationAll).
     */
    void deleteByConversationId(String conversationId);
}
