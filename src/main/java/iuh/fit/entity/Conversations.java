package iuh.fit.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.ConversationType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Conversations entity - Stores conversation/chat groups
 * Can be PRIVATE (1-1) or GROUP
 * Related to: Message, ConversationMember
 */
@Document(collection = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Conversations {
    
    @Id
    String conversationId;
    
    ConversationType type; // PRIVATE or GROUP
    String conversationName; // Group name (null for private chats)
    String avatarUrl; // Group avatar (null for private chats)
    String creatorId; // Reference to UserAuth (who created the group)
    LocalDateTime createdAt;
    String lastMessageId; // Reference to last Message
    Boolean isPinned;
    String groupDescription; // Group description
    LocalDateTime updatedAt;
}
