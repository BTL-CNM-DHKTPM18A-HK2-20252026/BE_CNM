package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.MessageType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Message entity - Stores chat messages
 * Related to: Conversations (conversationId), UserAuth (senderId)
 * Can have: MessageAttachment, MessageReaction
 */
@Document(collection = "message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message {
    
    @Id
    @Builder.Default
    String messageId = UUID.randomUUID().toString();

    String conversationId; // Reference to Conversations
    String senderId; // Reference to UserAuth (who sent the message)
    MessageType messageType;
    String content; // Text content or file URL
    String replyToMessageId; // Reference to another Message (for replies)
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Boolean isDeleted;
    Boolean isRecalled; // User can recall/unsend message
    Boolean isEdited;
}
