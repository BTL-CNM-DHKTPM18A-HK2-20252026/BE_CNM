package iuh.fit.entity;

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
    @Builder.Default
    String viewId = UUID.randomUUID().toString(); // Primary id for the view record

    String storyId; // Reference to Story
    String viewerId; // Reference to UserAuth (who viewed)
    java.time.LocalDateTime viewedAt;
    String reaction; // Optional reaction to story
}
