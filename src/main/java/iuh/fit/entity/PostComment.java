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
 * PostComment entity - Stores comments on posts
 * Related to: Post (postId), UserAuth (userId)
 * Supports nested comments (parentCommentId)
 */
@Document(collection = "post_comment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostComment {
    
    @Id
    String commentId;
    
    String postId; // Reference to Post
    String userId; // Reference to UserAuth (who commented)
    String content; // Comment text
    String parentCommentId; // Reference to parent PostComment (for nested comments)
    LocalDateTime createdAt;
}
