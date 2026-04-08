package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * MessageBucket entity - Groups 50 messages per document for optimized storage.
 *
 * This is the PRIMARY storage for messages. Instead of one document per
 * message,
 * messages are bucketed into groups of BUCKET_SIZE per conversation.
 * This reduces document count, index overhead, and improves read performance.
 *
 * The individual "message" collection is retained only for legacy services
 * (SearchService reindex, AiChatService) and will be phased out.
 */
@Document(collection = "message_bucket")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@CompoundIndexes({
        @CompoundIndex(name = "conv_seq_idx", def = "{'conversationId': 1, 'sequenceNumber': -1}"),
        @CompoundIndex(name = "conv_full_seq_idx", def = "{'conversationId': 1, 'isFull': 1, 'sequenceNumber': -1}"),
        @CompoundIndex(name = "msg_id_idx", def = "{'messages.messageId': 1}")
})
public class MessageBucket {

    public static final int BUCKET_SIZE = 50;

    @Id
    @Builder.Default
    String bucketId = UUID.randomUUID().toString();

    String conversationId;

    /** Monotonically increasing per conversation. Newest bucket has highest seq. */
    int sequenceNumber;

    /** Embedded messages ordered by createdAt ASC within the bucket. */
    @Builder.Default
    List<Message> messages = new ArrayList<>();

    int messageCount;

    /** True when messageCount >= BUCKET_SIZE. No more messages will be added. */
    @Builder.Default
    Boolean isFull = false;

    LocalDateTime firstMessageAt;
    LocalDateTime lastMessageAt;
    LocalDateTime createdAt;

    /** Optimistic locking — prevents race conditions in group chats. */
    @Version
    Long version;
}
