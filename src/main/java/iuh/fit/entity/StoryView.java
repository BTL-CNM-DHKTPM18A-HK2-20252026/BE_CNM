package iuh.fit.entity;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * StoryView entity - Tracks who viewed a story
 * Related to: Story (storyId), UserAuth (userId)
 */
@Document(collection = "story_view")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryView {
    
    @Id
    String storyId; // Reference to Story (composite key with userId)
    
    String userId; // Reference to UserAuth (who viewed)
    Date viewedAt;
    String reaction; // Optional reaction to story
}
