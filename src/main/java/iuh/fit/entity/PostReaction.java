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
 * PostReaction entity - Stores reactions/likes on posts
 * Related to: Post (postId), UserAuth (userId)
 */
@Document(collection = "post_reaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostReaction {
    
    @Id
    String postId; // Reference to Post (composite key with userId)
    
    String userId; // Reference to UserAuth (who reacted)
    String icon; // Reaction emoji (like, love, haha, etc.)
    LocalDateTime createdAt;
}
