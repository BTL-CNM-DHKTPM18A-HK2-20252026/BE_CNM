package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import iuh.fit.enums.ReactionType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * CommentReaction entity - Stores reactions/likes on comments
 * Related to: PostComment (commentId), UserAuth (userId)
 */
@Document(collection = "comment_reaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentReaction {
    
    @Id
    @Builder.Default
    String reactionId = UUID.randomUUID().toString();

    String commentId; // Reference to PostComment
    String userId; // Reference to UserAuth (who reacted)
    ReactionType reactionType; // Reaction enum
    LocalDateTime createdAt;
}
