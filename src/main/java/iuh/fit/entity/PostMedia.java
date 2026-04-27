package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * PostMedia entity - Stores media attachments for posts (images, videos)
 * Related to: Post (postId)
 */
@Document(collection = "post_media")
public class PostMedia {
    
    @Id
    private String mediaId = UUID.randomUUID().toString();
    
    private String postId; 
    private String url; 
    private String type; 
    private String altText; 
    private LocalDateTime createdAt;

    public PostMedia() {}

    public PostMedia(String mediaId, String postId, String url, String type, String altText, LocalDateTime createdAt) {
        this.mediaId = mediaId;
        this.postId = postId;
        this.url = url;
        this.type = type;
        this.altText = altText;
        this.createdAt = createdAt;
    }

    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static PostMediaBuilder builder() {
        return new PostMediaBuilder();
    }

    public static class PostMediaBuilder {
        private String mediaId = UUID.randomUUID().toString();
        private String postId;
        private String url;
        private String type;
        private String altText;
        private LocalDateTime createdAt;

        public PostMediaBuilder mediaId(String mediaId) { this.mediaId = mediaId; return this; }
        public PostMediaBuilder postId(String postId) { this.postId = postId; return this; }
        public PostMediaBuilder url(String url) { this.url = url; return this; }
        public PostMediaBuilder type(String type) { this.type = type; return this; }
        public PostMediaBuilder altText(String altText) { this.altText = altText; return this; }
        public PostMediaBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public PostMedia build() {
            return new PostMedia(mediaId, postId, url, type, altText, createdAt);
        }
    }
}
