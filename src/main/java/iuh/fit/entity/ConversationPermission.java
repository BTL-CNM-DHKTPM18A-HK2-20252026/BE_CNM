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
 * ConversationPermission entity - Stores group settings and permissions
 * Related to: Conversations (conversationId)
 */
@Document(collection = "conversation_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationPermission {

    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();

    String conversationId; // Reference to Conversations

    @Builder.Default
    Boolean canEditInfo = true; // true: Everyone, false: Only Admin/Deputy

    @Builder.Default
    Boolean canPinMessages = true;

    @Builder.Default
    Boolean canCreateNotes = true;

    @Builder.Default
    Boolean canCreatePolls = true;

    @Builder.Default
    Boolean canSendMessages = true;

    @Builder.Default
    Boolean isMemberApprovalRequired = false;

    @Builder.Default
    Boolean isHighlightAdminMessages = true;

    @Builder.Default
    Boolean canNewMembersReadRecentMessages = true;

    LocalDateTime updatedAt;
}
