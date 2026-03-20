package iuh.fit.entity;

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
 * Story entity - Stores user stories (24-hour content)
 * Related to: UserAuth (userId)
 * Can have: StoryView
 */
@Document(collection = "story")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Story {
    
    @Id
    @Builder.Default
    String storyId = UUID.randomUUID().toString();

    String authorId; // Reference to UserAuth (who created the story)
    String mediaUrl; // Story image/video URL
    String mediaType; // IMAGE, VIDEO, TEXT
    Integer duration; // Duration in seconds
    java.time.LocalDateTime expiresAt; // When story expires (24 hours)
    PrivacyLevel privacy; // Who can see this story
    String caption; // Text caption for story
    Boolean isDeleted;
    java.time.LocalDateTime createdAt;
}
