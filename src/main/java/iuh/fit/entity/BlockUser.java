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
 * BlockUser entity - Stores user blocking relationships
 * One user blocks another user
 */
@Document(collection = "block_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockUser {
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    String blockerId; // User who blocked
    String blockedId; // User who was blocked
    String blockedUserId; // alias for repository queries expecting 'blockedUserId'
    LocalDateTime blockedAt;
    String reason;
    Boolean blockMessages;
    Boolean blockCalls;
    Boolean hidePosts;
}
