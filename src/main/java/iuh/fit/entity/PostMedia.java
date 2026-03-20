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
 * PostMedia entity - Stores media attachments for posts (images, videos)
 * Related to: Post (postId)
 */
@Document(collection = "post_media")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostMedia {
    
    @Id
    @Builder.Default
    String mediaId = UUID.randomUUID().toString();
    
    String postId; // Reference to Post
    String url; // Media URL
    String type; // IMAGE, VIDEO, etc.
    LocalDateTime createdAt;
}
