package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.ConversationStatus;
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
    @Builder.Default
    String conversationId = UUID.randomUUID().toString();

    ConversationType conversationType;

    @Builder.Default
    ConversationStatus conversationStatus = ConversationStatus.NORMAL;

    List<String> participants; // Used for PRIVATE chats: [user1Id, user2Id]
    String conversationName; // Group name or user display name
    String avatarUrl; // Group avatar or user avatar
    String creatorId; // User who created the group
    LocalDateTime createdAt;

    // Denormalized fields for quick access to latest activity
    String lastMessageId;
    String lastMessageContent;
    LocalDateTime lastMessageTime;
    String lastMessageSenderId;
    String lastMessageSenderName;

    Boolean isPinned; // Only used for global pinning (optional)
    String groupDescription;
    LocalDateTime updatedAt;
    Boolean isDeleted;
    String autoDeleteDuration; // 1h, 1d, 1w, etc.
    String invitationLink; // Group invitation link

    String aiSummary;
    LocalDateTime aiSummaryUpdatedAt;
}
