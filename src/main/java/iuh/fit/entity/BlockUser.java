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
    String id;
    
    String blockerId; // User who blocked
    String blockedId; // User who was blocked
    LocalDateTime blockedAt;
    String reason;
    Boolean blockMessages;
    Boolean blockCalls;
    Boolean hidePosts;
}
