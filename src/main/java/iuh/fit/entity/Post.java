package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.PrivacyLevel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Post entity - Stores user posts/status updates
 * Related to: UserAuth (userId)
 * Can have: PostMedia, PostReaction, PostComment
 */
@Document(collection = "post")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Post {
    
    @Id
    @Builder.Default
    String postId = UUID.randomUUID().toString();

    String authorId; // Reference to UserAuth (who created the post)
    String content; // Post text content
    PrivacyLevel privacy; // Who can see this post
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Boolean isDeleted;
    String location; // Location tag
    Integer commentCount; // Denormalized for performance
}
