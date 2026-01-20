package iuh.fit.entity;

import java.time.LocalDateTime;

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
    String conversationId; // Reference to Conversations (composite key with userId)
    
    String userId; // Reference to UserAuth
    MemberRole role; // ADMIN, DEPUTY, MEMBER
    LocalDateTime joinedAt;
    String nickname; // Custom nickname in this conversation
}
