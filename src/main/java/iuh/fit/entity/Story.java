package iuh.fit.entity;

import java.util.Date;

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
    String storyId;
    
    String userId; // Reference to UserAuth (who created the story)
    String mediaUrl; // Story image/video URL
    String type; // IMAGE, VIDEO, TEXT
    Integer duration; // Duration in seconds
    Date expiresAt; // When story expires (24 hours)
    PrivacyLevel privacy; // Who can see this story
    String content; // Text content for text stories
}
