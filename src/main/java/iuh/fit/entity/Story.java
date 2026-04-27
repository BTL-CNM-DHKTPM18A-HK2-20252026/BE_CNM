package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.PrivacyLevel;

/**
 * Story entity - Stores user stories (24-hour content)
 * Related to: UserAuth (userId)
 * Can have: StoryView
 */
@Document(collection = "story")
public class Story {
    
    @Id
    private String storyId = UUID.randomUUID().toString();

    private String authorId; 
    private String mediaUrl; 
    private String mediaType; 
    private Integer duration; 
    private LocalDateTime expiresAt; 
    private PrivacyLevel privacy; 
    private String caption; 
    private String background; 
    private Boolean isDeleted;
    private LocalDateTime createdAt;

    public Story() {}

    public Story(String storyId, String authorId, String mediaUrl, String mediaType, Integer duration, 
                LocalDateTime expiresAt, PrivacyLevel privacy, String caption, String background, 
                Boolean isDeleted, LocalDateTime createdAt) {
        this.storyId = storyId;
        this.authorId = authorId;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.duration = duration;
        this.expiresAt = expiresAt;
        this.privacy = privacy;
        this.caption = caption;
        this.background = background;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
    }

    public String getStoryId() { return storyId; }
    public void setStoryId(String storyId) { this.storyId = storyId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public PrivacyLevel getPrivacy() { return privacy; }
    public void setPrivacy(PrivacyLevel privacy) { this.privacy = privacy; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static StoryBuilder builder() {
        return new StoryBuilder();
    }

    public static class StoryBuilder {
        private String storyId = UUID.randomUUID().toString();
        private String authorId;
        private String mediaUrl;
        private String mediaType;
        private Integer duration;
        private LocalDateTime expiresAt;
        private PrivacyLevel privacy;
        private String caption;
        private String background;
        private Boolean isDeleted;
        private LocalDateTime createdAt;

        public StoryBuilder storyId(String storyId) { this.storyId = storyId; return this; }
        public StoryBuilder authorId(String authorId) { this.authorId = authorId; return this; }
        public StoryBuilder mediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; return this; }
        public StoryBuilder mediaType(String mediaType) { this.mediaType = mediaType; return this; }
        public StoryBuilder duration(Integer duration) { this.duration = duration; return this; }
        public StoryBuilder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
        public StoryBuilder privacy(PrivacyLevel privacy) { this.privacy = privacy; return this; }
        public StoryBuilder caption(String caption) { this.caption = caption; return this; }
        public StoryBuilder background(String background) { this.background = background; return this; }
        public StoryBuilder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public StoryBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Story build() {
            return new Story(storyId, authorId, mediaUrl, mediaType, duration, expiresAt, privacy, caption, background, isDeleted, createdAt);
        }
    }
}
