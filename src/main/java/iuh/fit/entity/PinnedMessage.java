package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * PinnedMessage entity - Stores pinned messages in conversations
 * Related to: Message (messageId), Conversations (conversationId)
 */
@Document(collection = "pinned_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PinnedMessage {
    
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    
    String messageId; // Reference to Message
    String conversationId; // Reference to Conversations
    String type; // Type of pin
    LocalDateTime pinnedAt;
}
