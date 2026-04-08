package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.ReactionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * MessageReaction entity - Stores reactions to messages
 * Related to: Message (messageId), UserAuth (userId)
 */
@Document(collection = "message_reaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageReaction {
    
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    
    @Indexed(name = "messageId_idx")
    String messageId; // Reference to Message
    
    String userId; // Reference to UserAuth (who reacted)
    
    ReactionType icon; // Emoji reaction type
    
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();
}
