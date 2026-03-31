package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.ConversationType;
import iuh.fit.enums.ConversationStatus;
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
    @Builder.Default
    String conversationId = UUID.randomUUID().toString();

    ConversationType conversationType; // PRIVATE or GROUP
    @Builder.Default
    ConversationStatus conversationStatus = ConversationStatus.NORMAL; // NORMAL, PENDING, BLOCKED
    List<String> participants; // Members in a sorted list (e.g., ["user1", "user2"])
    String conversationName; // Group name (null for private chats)
    String avatarUrl; // Group avatar (null for private chats)
    String creatorId; // Reference to UserAuth (who created the group)
    LocalDateTime createdAt;
    String lastMessageId; // Reference to last Message
    String lastMessageContent; // Denormalized content for quick snippet display
    LocalDateTime lastMessageTime; // Denormalized time for sorting/ordering
    Boolean isPinned;
    String groupDescription; // Group description
    LocalDateTime updatedAt;
    Boolean isDeleted; // Soft-delete flag
    String autoDeleteDuration; // off, 1d, 7d, 30d
}
