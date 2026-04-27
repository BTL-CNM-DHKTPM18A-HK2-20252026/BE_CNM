package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.PostType;
import iuh.fit.enums.PrivacyLevel;

/**
 * Post entity - Stores user posts/status updates
 * Related to: UserAuth (userId)
 * Can have: PostMedia, PostReaction, PostComment
 */
@Document(collection = "post")
public class Post {
    
    @Id
    private String postId = UUID.randomUUID().toString();

    private String authorId; 
    private String content; 
    private PrivacyLevel privacy; 
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
    private String location; 
    private Integer commentCount; 
    private Boolean hideLikes = false;
    private Boolean turnOffComments = false;
    private List<PostMedia> media = new ArrayList<>();
    private PostType type = PostType.TEXT;
    private LinkMetadata linkMetadata; 

    public Post() {}

    public Post(String postId, String authorId, String content, PrivacyLevel privacy, LocalDateTime createdAt, 
                LocalDateTime updatedAt, Boolean isDeleted, String location, Integer commentCount, 
                Boolean hideLikes, Boolean turnOffComments, List<PostMedia> media, PostType type, 
                LinkMetadata linkMetadata) {
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.privacy = privacy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
        this.location = location;
        this.commentCount = commentCount;
        this.hideLikes = hideLikes;
        this.turnOffComments = turnOffComments;
        this.media = media;
        this.type = type;
        this.linkMetadata = linkMetadata;
    }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public PrivacyLevel getPrivacy() { return privacy; }
    public void setPrivacy(PrivacyLevel privacy) { this.privacy = privacy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Boolean getHideLikes() { return hideLikes; }
    public void setHideLikes(Boolean hideLikes) { this.hideLikes = hideLikes; }
    public Boolean getTurnOffComments() { return turnOffComments; }
    public void setTurnOffComments(Boolean turnOffComments) { this.turnOffComments = turnOffComments; }
    public List<PostMedia> getMedia() { return media; }
    public void setMedia(List<PostMedia> media) { this.media = media; }
    public PostType getType() { return type; }
    public void setType(PostType type) { this.type = type; }
    public LinkMetadata getLinkMetadata() { return linkMetadata; }
    public void setLinkMetadata(LinkMetadata linkMetadata) { this.linkMetadata = linkMetadata; }

    public static PostBuilder builder() {
        return new PostBuilder();
    }

    public static class PostBuilder {
        private String postId = UUID.randomUUID().toString();
        private String authorId;
        private String content;
        private PrivacyLevel privacy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean isDeleted;
        private String location;
        private Integer commentCount;
        private Boolean hideLikes = false;
        private Boolean turnOffComments = false;
        private List<PostMedia> media = new ArrayList<>();
        private PostType type = PostType.TEXT;
        private LinkMetadata linkMetadata;

        public PostBuilder postId(String postId) { this.postId = postId; return this; }
        public PostBuilder authorId(String authorId) { this.authorId = authorId; return this; }
        public PostBuilder content(String content) { this.content = content; return this; }
        public PostBuilder privacy(PrivacyLevel privacy) { this.privacy = privacy; return this; }
        public PostBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PostBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public PostBuilder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public PostBuilder location(String location) { this.location = location; return this; }
        public PostBuilder commentCount(Integer commentCount) { this.commentCount = commentCount; return this; }
        public PostBuilder hideLikes(Boolean hideLikes) { this.hideLikes = hideLikes; return this; }
        public PostBuilder turnOffComments(Boolean turnOffComments) { this.turnOffComments = turnOffComments; return this; }
        public PostBuilder media(List<PostMedia> media) { this.media = media; return this; }
        public PostBuilder type(PostType type) { this.type = type; return this; }
        public PostBuilder linkMetadata(LinkMetadata linkMetadata) { this.linkMetadata = linkMetadata; return this; }

        public Post build() {
            return new Post(postId, authorId, content, privacy, createdAt, updatedAt, isDeleted, location, commentCount, hideLikes, turnOffComments, media, type, linkMetadata);
        }
    }
}
