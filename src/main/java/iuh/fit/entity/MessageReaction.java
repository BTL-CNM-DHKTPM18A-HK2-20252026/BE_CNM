package iuh.fit.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
    String messageId; // Reference to Message (composite key with userId)
    
    String userId; // Reference to UserAuth (who reacted)
    String icon; // Emoji or reaction icon
    LocalDateTime createdAt;
    Integer quantity; // Number of times this reaction was used
}
