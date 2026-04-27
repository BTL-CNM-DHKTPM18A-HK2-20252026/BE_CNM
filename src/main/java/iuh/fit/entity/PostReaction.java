package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import iuh.fit.enums.ReactionType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * PostReaction entity - Stores reactions/likes on posts
 * Related to: Post (postId), UserAuth (userId)
 */
@Document(collection = "post_reaction")
public class PostReaction {
    
    @Id
    private String reactionId = UUID.randomUUID().toString();

    private String postId; 
    private String userId; 
    private ReactionType reactionType; 
    private LocalDateTime createdAt;

    public PostReaction() {}

    public PostReaction(String reactionId, String postId, String userId, ReactionType reactionType, LocalDateTime createdAt) {
        this.reactionId = reactionId;
        this.postId = postId;
        this.userId = userId;
        this.reactionType = reactionType;
        this.createdAt = createdAt;
    }

    public String getReactionId() { return reactionId; }
    public void setReactionId(String reactionId) { this.reactionId = reactionId; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public ReactionType getReactionType() { return reactionType; }
    public void setReactionType(ReactionType reactionType) { this.reactionType = reactionType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static PostReactionBuilder builder() {
        return new PostReactionBuilder();
    }

    public static class PostReactionBuilder {
        private String reactionId = UUID.randomUUID().toString();
        private String postId;
        private String userId;
        private ReactionType reactionType;
        private LocalDateTime createdAt;

        public PostReactionBuilder reactionId(String reactionId) { this.reactionId = reactionId; return this; }
        public PostReactionBuilder postId(String postId) { this.postId = postId; return this; }
        public PostReactionBuilder userId(String userId) { this.userId = userId; return this; }
        public PostReactionBuilder reactionType(ReactionType reactionType) { this.reactionType = reactionType; return this; }
        public PostReactionBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Post build() {
            return new PostReaction(reactionId, postId, userId, reactionType, createdAt);
        }
    }
}
