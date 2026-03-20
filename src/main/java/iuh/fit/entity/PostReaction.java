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
    @Builder.Default
    String reactionId = UUID.randomUUID().toString();

    String postId; // Reference to Post
    String userId; // Reference to UserAuth (who reacted)
    iuh.fit.enums.ReactionType reactionType; // Reaction enum
    LocalDateTime createdAt;
}
