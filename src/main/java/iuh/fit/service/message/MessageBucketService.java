package iuh.fit.service.message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import iuh.fit.entity.Message;
import iuh.fit.entity.MessageBucket;
import iuh.fit.enums.MessageType;
import iuh.fit.repository.MessageBucketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing message buckets — the PRIMARY message storage.
 *
 * All message CRUD goes through buckets. The individual "message" collection
 * is only retained for legacy services (SearchService reindex, AiChatService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBucketService {

        private final MessageBucketRepository messageBucketRepository;
        private final MongoTemplate mongoTemplate;

        /**
         * Add a message to the appropriate bucket for its conversation.
         * If the latest bucket is full or doesn't exist, a new bucket is created.
         *
         * @return the sequence number of the bucket the message was added to
         */
        public int addMessageToBucket(Message message) {
                String convId = message.getConversationId();
                LocalDateTime now = message.getCreatedAt();

                MessageBucket bucket = messageBucketRepository
                                .findFirstByConversationIdAndIsFullFalseOrderBySequenceNumberDesc(convId)
                                .orElse(null);

                if (bucket == null) {
                        int nextSeq = messageBucketRepository
                                        .findFirstByConversationIdOrderBySequenceNumberDesc(convId)
                                        .map(b -> b.getSequenceNumber() + 1)
                                        .orElse(0);

                        bucket = MessageBucket.builder()
                                        .conversationId(convId)
                                        .sequenceNumber(nextSeq)
                                        .messages(new ArrayList<>())
                                        .messageCount(0)
                                        .isFull(false)
                                        .firstMessageAt(now)
                                        .createdAt(now)
                                        .build();
                }

                bucket.getMessages().add(message);
                bucket.setMessageCount(bucket.getMessages().size());
                bucket.setLastMessageAt(now);

                if (bucket.getFirstMessageAt() == null) {
                        bucket.setFirstMessageAt(now);
                }

                if (bucket.getMessageCount() >= MessageBucket.BUCKET_SIZE) {
                        bucket.setIsFull(true);
                }

                messageBucketRepository.save(bucket);
                log.debug("Message {} added to bucket {} (seq={}, count={})",
                                message.getMessageId(), bucket.getBucketId(),
                                bucket.getSequenceNumber(), bucket.getMessageCount());
                return bucket.getSequenceNumber();
        }

        /**
         * Check whether a message with the given ID has already been persisted in any
         * bucket.
         * Used by the Kafka consumer for idempotency.
         */
        public boolean existsByMessageId(String messageId) {
                org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query(
                                Criteria.where("messages.messageId").is(messageId));
                return mongoTemplate.exists(query, MessageBucket.class);
        }

        /**
         * Get paginated messages for a conversation using bucket aggregation.
         * Messages are returned newest-first (DESC by createdAt).
         */
        public Page<Message> getConversationMessages(String conversationId, Pageable pageable) {
                long totalMessages = countMessages(conversationId);

                if (totalMessages == 0) {
                        return Page.empty(pageable);
                }

                Aggregation aggregation = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.sort(Sort.Direction.DESC, "sequenceNumber"),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                                Aggregation.skip((long) pageable.getOffset()),
                                Aggregation.limit(pageable.getPageSize()));

                AggregationResults<Message> results = mongoTemplate.aggregate(
                                aggregation, "message_bucket", Message.class);

                return new PageImpl<>(results.getMappedResults(), pageable, totalMessages);
        }

        /**
         * Get messages before a cursor timestamp (cursor-based pagination).
         * Used for infinite scroll loading older messages.
         */
        public List<Message> getMessagesBefore(String conversationId, LocalDateTime beforeTime, int size) {
                Aggregation aggregation = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.sort(Sort.Direction.DESC, "sequenceNumber"),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("createdAt").lt(beforeTime)),
                                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                                Aggregation.limit(size));

                AggregationResults<Message> results = mongoTemplate.aggregate(
                                aggregation, "message_bucket", Message.class);

                return results.getMappedResults();
        }

        /**
         * Get messages after a cursor timestamp (for "around message" feature).
         */
        public List<Message> getMessagesAfter(String conversationId, LocalDateTime afterTime, int size) {
                Aggregation aggregation = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.sort(Sort.Direction.ASC, "sequenceNumber"),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("createdAt").gt(afterTime)),
                                Aggregation.sort(Sort.Direction.ASC, "createdAt"),
                                Aggregation.limit(size));

                AggregationResults<Message> results = mongoTemplate.aggregate(
                                aggregation, "message_bucket", Message.class);

                return results.getMappedResults();
        }

        /**
         * Count total messages in a conversation (sum of all bucket messageCounts).
         */
        public long countMessages(String conversationId) {
                Aggregation countAgg = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.group().sum("messageCount").as("total"));

                org.bson.Document result = mongoTemplate
                                .aggregate(countAgg, "message_bucket", org.bson.Document.class)
                                .getUniqueMappedResult();

                if (result == null) {
                        return 0;
                }
                return result.getInteger("total", 0);
        }

        /**
         * Find a single message by its ID from within a bucket.
         * Uses the multikey index on messages.messageId for O(1) lookup.
         */
        public Optional<Message> findMessageById(String messageId) {
                return messageBucketRepository.findByMessagesMessageId(messageId)
                                .flatMap(bucket -> bucket.getMessages().stream()
                                                .filter(m -> m.getMessageId().equals(messageId))
                                                .findFirst());
        }

        /**
         * Get all messages in a conversation (unwinds all buckets).
         * Used for bulk operations like clearConversationAll.
         */
        public List<Message> findAllByConversationId(String conversationId) {
                Aggregation aggregation = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.sort(Sort.Direction.ASC, "sequenceNumber"),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"));

                AggregationResults<Message> results = mongoTemplate.aggregate(
                                aggregation, "message_bucket", Message.class);
                return results.getMappedResults();
        }

        /**
         * Get media messages (IMAGE, VIDEO, MEDIA) in a conversation.
         */
        public List<Message> findByConversationIdAndMessageTypeIn(
                        String conversationId, Collection<String> messageTypes) {
                Aggregation aggregation = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("messageType").in(messageTypes)),
                                Aggregation.sort(Sort.Direction.DESC, "createdAt"));

                AggregationResults<Message> results = mongoTemplate.aggregate(
                                aggregation, "message_bucket", Message.class);
                return results.getMappedResults();
        }

        /**
         * Get link messages in a conversation.
         */
        public List<Message> findLinksByConversationId(String conversationId) {
                Aggregation aggregation = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("messageType").is(MessageType.LINK.name())),
                                Aggregation.sort(Sort.Direction.DESC, "createdAt"));

                AggregationResults<Message> results = mongoTemplate.aggregate(
                                aggregation, "message_bucket", Message.class);
                return results.getMappedResults();
        }

        /**
         * Sync an edited message into the bucket (update content, isEdited,
         * editHistory).
         */
        public void syncMessageUpdate(Message updatedMessage) {
                Query query = new Query(Criteria.where("messages.messageId").is(updatedMessage.getMessageId()));
                Update update = new Update()
                                .set("messages.$.content", updatedMessage.getContent())
                                .set("messages.$.isEdited", updatedMessage.getIsEdited())
                                .set("messages.$.updatedAt", updatedMessage.getUpdatedAt())
                                .set("messages.$.editHistory", updatedMessage.getEditHistory());
                mongoTemplate.updateFirst(query, update, MessageBucket.class);
        }

        /**
         * Sync a recalled message into the bucket.
         */
        public void syncMessageRecall(String messageId, LocalDateTime updatedAt) {
                Query query = new Query(Criteria.where("messages.messageId").is(messageId));
                Update update = new Update()
                                .set("messages.$.isRecalled", true)
                                .set("messages.$.updatedAt", updatedAt);
                mongoTemplate.updateFirst(query, update, MessageBucket.class);
        }

        /**
         * Sync a deleted message into the bucket.
         */
        public void syncMessageDelete(String messageId) {
                Query query = new Query(Criteria.where("messages.messageId").is(messageId));
                Update update = new Update()
                                .set("messages.$.isDeleted", true);
                mongoTemplate.updateFirst(query, update, MessageBucket.class);
        }

        /**
         * Sync local delete into the bucket (add userId to localDeletedBy).
         */
        public void syncMessageLocalDelete(String messageId, List<String> localDeletedBy) {
                Query query = new Query(Criteria.where("messages.messageId").is(messageId));
                Update update = new Update()
                                .set("messages.$.localDeletedBy", localDeletedBy);
                mongoTemplate.updateFirst(query, update, MessageBucket.class);
        }

        /**
         * Delete all buckets for a conversation.
         */
        public void deleteByConversationId(String conversationId) {
                messageBucketRepository.deleteByConversationId(conversationId);
                log.info("Deleted all buckets for conversation: {}", conversationId);
        }

        /**
         * Backfill buckets from existing individual messages.
         * Call this once to migrate existing data into the bucket structure.
         */
        public int backfillBuckets(String conversationId, List<Message> messages) {
                if (messages.isEmpty()) {
                        return 0;
                }

                // Sort by createdAt ASC
                List<Message> sorted = messages.stream()
                                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                                .collect(Collectors.toList());

                int bucketCount = 0;
                for (int i = 0; i < sorted.size(); i += MessageBucket.BUCKET_SIZE) {
                        int end = Math.min(i + MessageBucket.BUCKET_SIZE, sorted.size());
                        List<Message> chunk = new ArrayList<>(sorted.subList(i, end));

                        MessageBucket bucket = MessageBucket.builder()
                                        .conversationId(conversationId)
                                        .sequenceNumber(bucketCount)
                                        .messages(chunk)
                                        .messageCount(chunk.size())
                                        .isFull(chunk.size() >= MessageBucket.BUCKET_SIZE)
                                        .firstMessageAt(chunk.get(0).getCreatedAt())
                                        .lastMessageAt(chunk.get(chunk.size() - 1).getCreatedAt())
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        messageBucketRepository.save(bucket);
                        bucketCount++;
                }

                log.info("Backfilled {} buckets for conversation {} ({} messages)",
                                bucketCount, conversationId, sorted.size());
                return bucketCount;
        }

        /**
         * Count unread messages in a conversation for a given user
         * (messages not sent by excludeSenderId and not deleted).
         */
        public long countUnread(String conversationId, String excludeSenderId) {
                Aggregation agg = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("isDeleted").ne(true)
                                                .and("senderId").ne(excludeSenderId)),
                                Aggregation.count().as("total"));
                org.bson.Document result = mongoTemplate
                                .aggregate(agg, "message_bucket", org.bson.Document.class)
                                .getUniqueMappedResult();
                return result == null ? 0L : ((Number) result.get("total")).longValue();
        }

        /**
         * Count unread messages after a given timestamp in a conversation
         * (messages not sent by excludeSenderId, not deleted, created after
         * {@code after}).
         */
        public long countUnreadAfter(String conversationId, LocalDateTime after, String excludeSenderId) {
                Aggregation agg = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("isDeleted").ne(true)
                                                .and("senderId").ne(excludeSenderId)
                                                .and("createdAt").gt(after)),
                                Aggregation.count().as("total"));
                org.bson.Document result = mongoTemplate
                                .aggregate(agg, "message_bucket", org.bson.Document.class)
                                .getUniqueMappedResult();
                return result == null ? 0L : ((Number) result.get("total")).longValue();
        }

        /**
         * Find AI-generated messages in given conversations created after
         * {@code after}.
         * Used for daily AI token usage tracking.
         */
        public List<Message> findAiMessagesByConversationIdsAfter(List<String> conversationIds,
                        LocalDateTime after) {
                if (conversationIds == null || conversationIds.isEmpty()) {
                        return List.of();
                }
                Aggregation agg = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").in(conversationIds)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("aiGenerated").is(true)
                                                .and("createdAt").gte(after)));
                return mongoTemplate.aggregate(agg, "message_bucket", Message.class).getMappedResults();
        }

        /**
         * Find recent messages sent by a specific user across all conversations,
         * ordered by createdAt descending. Used by AI profile snapshot.
         */
        public Page<Message> findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable pageable) {
                Aggregation agg = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("messages.senderId").is(senderId)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("senderId").is(senderId)),
                                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                                Aggregation.skip(pageable.getOffset()),
                                Aggregation.limit(pageable.getPageSize()));
                List<Message> messages = mongoTemplate.aggregate(agg, "message_bucket", Message.class)
                                .getMappedResults();
                return new PageImpl<>(messages, pageable, messages.size());
        }

        /**
         * Delete messages older than {@code cutoff} from all buckets of a conversation.
         * Modifies bucket documents in-place using $pull.
         *
         * @return approximate count of deleted messages
         */
        public long deleteByConversationIdAndCreatedAtBefore(String conversationId, LocalDateTime cutoff) {
                Aggregation countAgg = Aggregation.newAggregation(
                                Aggregation.match(Criteria.where("conversationId").is(conversationId)),
                                Aggregation.unwind("messages"),
                                Aggregation.replaceRoot("messages"),
                                Aggregation.match(Criteria.where("createdAt").lt(cutoff)),
                                Aggregation.count().as("total"));
                AggregationResults<org.bson.Document> countResult = mongoTemplate.aggregate(
                                countAgg, "message_bucket", org.bson.Document.class);
                long deleted = countResult.getMappedResults().isEmpty() ? 0
                                : ((Number) countResult.getMappedResults().get(0).get("total")).longValue();
                if (deleted > 0) {
                        Query query = new Query(Criteria.where("conversationId").is(conversationId));
                        Update update = new Update().pull("messages",
                                        new org.bson.Document("createdAt",
                                                        new org.bson.Document("$lt",
                                                                        java.util.Date.from(cutoff
                                                                                        .atZone(java.time.ZoneId
                                                                                                        .systemDefault())
                                                                                        .toInstant()))));
                        mongoTemplate.updateMulti(query, update, "message_bucket");
                }
                return deleted;
        }
}
