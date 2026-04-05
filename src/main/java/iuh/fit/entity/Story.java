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

    String authorId;
    String mediaUrl;
    String mediaType; // IMAGE | VIDEO | TEXT

    Integer duration; // only for VIDEO

    String caption;

    @Builder.Default
    PrivacyLevel privacy = PrivacyLevel.FRIEND_ONLY;

    @Builder.Default
    Boolean isDeleted = false;

    LocalDateTime createdAt;
    LocalDateTime expiresAt; // now + 24h
}
