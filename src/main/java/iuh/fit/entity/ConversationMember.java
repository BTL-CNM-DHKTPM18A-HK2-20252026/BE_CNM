package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.MemberRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * ConversationMember entity - Stores members of a conversation
 * Related to: Conversations (conversationId), UserAuth (userId)
 */
@Document(collection = "conversation_member")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationMember {

    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    String conversationId; // Reference to Conversations
    String userId; // Reference to UserAuth
    MemberRole role; // ADMIN, DEPUTY, MEMBER
    LocalDateTime joinedAt;
    String nickname; // Custom nickname in this conversation
    String lastReadMessageId; // Last message this member has read
    LocalDateTime lastReadAt;
    String lastDeliveredMessageId; // Last message delivered to this member's device
    LocalDateTime lastDeliveredAt;

    @Builder.Default
    Boolean isPinned = false; // Per-user pin status

    LocalDateTime pinnedAt; // Last time user pinned this conversation

    @Builder.Default
    Boolean isHidden = false; // Per-user soft delete (hide conversation)

    String conversationTag; // Per-user tag: customer, family, work, friends, reply_later, colleagues

    LocalDateTime mutedUntil; // Null = not muted, future date = muted until, max = forever

    @Builder.Default
    Boolean isMarkedUnread = false; // Per-user mark as unread

    String wallpaperUrl; // Per-user chat wallpaper URL for this conversation
}
