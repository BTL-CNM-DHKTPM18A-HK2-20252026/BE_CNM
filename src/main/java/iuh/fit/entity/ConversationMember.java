package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.MemberRole;

/**
 * ConversationMember entity - Stores members of a conversation
 * Related to: Conversations (conversationId), UserAuth (userId)
 */
@Document(collection = "conversation_member")
public class ConversationMember {

    @Id
    private String id = UUID.randomUUID().toString();
    private String conversationId;
    private String userId;
    private MemberRole role;
    private LocalDateTime joinedAt;
    private String nickname;
    private String lastReadMessageId;
    private LocalDateTime lastReadAt;
    private Boolean isPinned = false;
    private LocalDateTime pinnedAt;
    private Boolean isHidden = false;
    private String conversationTag;
    private LocalDateTime mutedUntil;
    private Boolean isMarkedUnread = false;
    private String wallpaperUrl;

    public ConversationMember() {}

    public ConversationMember(String id, String conversationId, String userId, MemberRole role, LocalDateTime joinedAt, 
                            String nickname, String lastReadMessageId, LocalDateTime lastReadAt, Boolean isPinned, 
                            LocalDateTime pinnedAt, Boolean isHidden, String conversationTag, LocalDateTime mutedUntil, 
                            Boolean isMarkedUnread, String wallpaperUrl) {
        this.id = id;
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.nickname = nickname;
        this.lastReadMessageId = lastReadMessageId;
        this.lastReadAt = lastReadAt;
        this.isPinned = isPinned;
        this.pinnedAt = pinnedAt;
        this.isHidden = isHidden;
        this.conversationTag = conversationTag;
        this.mutedUntil = mutedUntil;
        this.isMarkedUnread = isMarkedUnread;
        this.wallpaperUrl = wallpaperUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(String lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }
    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }
    public Boolean getIsPinned() { return isPinned; }
    public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
    public LocalDateTime getPinnedAt() { return pinnedAt; }
    public void setPinnedAt(LocalDateTime pinnedAt) { this.pinnedAt = pinnedAt; }
    public Boolean getIsHidden() { return isHidden; }
    public void setIsHidden(Boolean isHidden) { this.isHidden = isHidden; }
    public String getConversationTag() { return conversationTag; }
    public void setConversationTag(String conversationTag) { this.conversationTag = conversationTag; }
    public LocalDateTime getMutedUntil() { return mutedUntil; }
    public void setMutedUntil(LocalDateTime mutedUntil) { this.mutedUntil = mutedUntil; }
    public Boolean getIsMarkedUnread() { return isMarkedUnread; }
    public void setIsMarkedUnread(Boolean isMarkedUnread) { this.isMarkedUnread = isMarkedUnread; }
    public String getWallpaperUrl() { return wallpaperUrl; }
    public void setWallpaperUrl(String wallpaperUrl) { this.wallpaperUrl = wallpaperUrl; }

    public static ConversationMemberBuilder builder() {
        return new ConversationMemberBuilder();
    }

    public static class ConversationMemberBuilder {
        private String id = UUID.randomUUID().toString();
        private String conversationId;
        private String userId;
        private MemberRole role;
        private LocalDateTime joinedAt;
        private String nickname;
        private String lastReadMessageId;
        private LocalDateTime lastReadAt;
        private Boolean isPinned = false;
        private LocalDateTime pinnedAt;
        private Boolean isHidden = false;
        private String conversationTag;
        private LocalDateTime mutedUntil;
        private Boolean isMarkedUnread = false;
        private String wallpaperUrl;

        public ConversationMemberBuilder id(String id) { this.id = id; return this; }
        public ConversationMemberBuilder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public ConversationMemberBuilder userId(String userId) { this.userId = userId; return this; }
        public ConversationMemberBuilder role(MemberRole role) { this.role = role; return this; }
        public ConversationMemberBuilder joinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; return this; }
        public ConversationMemberBuilder nickname(String nickname) { this.nickname = nickname; return this; }
        public ConversationMemberBuilder lastReadMessageId(String lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; return this; }
        public ConversationMemberBuilder lastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; return this; }
        public ConversationMemberBuilder isPinned(Boolean isPinned) { this.isPinned = isPinned; return this; }
        public ConversationMemberBuilder pinnedAt(LocalDateTime pinnedAt) { this.pinnedAt = pinnedAt; return this; }
        public ConversationMemberBuilder isHidden(Boolean isHidden) { this.isHidden = isHidden; return this; }
        public ConversationMemberBuilder conversationTag(String conversationTag) { this.conversationTag = conversationTag; return this; }
        public ConversationMemberBuilder mutedUntil(LocalDateTime mutedUntil) { this.mutedUntil = mutedUntil; return this; }
        public ConversationMemberBuilder isMarkedUnread(Boolean isMarkedUnread) { this.isMarkedUnread = isMarkedUnread; return this; }
        public ConversationMemberBuilder wallpaperUrl(String wallpaperUrl) { this.wallpaperUrl = wallpaperUrl; return this; }

        public ConversationMember build() {
            return new ConversationMember(id, conversationId, userId, role, joinedAt, nickname, lastReadMessageId, lastReadAt, isPinned, pinnedAt, isHidden, conversationTag, mutedUntil, isMarkedUnread, wallpaperUrl);
        }
    }
}
