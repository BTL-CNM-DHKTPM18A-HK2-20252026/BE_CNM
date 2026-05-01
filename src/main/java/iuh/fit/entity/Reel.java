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
 * Reel entity - Stores short videos (Reels)
 * Related to: UserAuth (authorId)
 */
@Document(collection = "reel")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Reel {
    
    @Id
    @Builder.Default
    String reelId = UUID.randomUUID().toString();

    String authorId; // Reference to UserAuth (who created the reel)
    String videoUrl; // Vertical video URL
    String thumbnailUrl; // Video thumbnail image URL
    String caption; // Text caption
    
    String musicId; // Reference to Music track if available
    String musicTitle; // Display title of the music
    String musicArtist; // Display artist of the music
    
    @Builder.Default
    Integer likesCount = 0;
    
    @Builder.Default
    Integer commentsCount = 0;
    
    @Builder.Default
    Integer sharesCount = 0;
    
    @Builder.Default
    Integer viewsCount = 0;
    
    @Builder.Default
    PrivacyLevel privacy = PrivacyLevel.PUBLIC;
    
    @Builder.Default
    Boolean isDeleted = false;
    
    @Builder.Default
    Boolean allowComments = true;
    
    @Builder.Default
    Boolean allowSharing = true;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
