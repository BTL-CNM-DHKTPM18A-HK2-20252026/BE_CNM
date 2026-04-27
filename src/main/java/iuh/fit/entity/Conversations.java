package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.ConversationType;
import iuh.fit.enums.ConversationStatus;

/**
 * Conversations entity - Stores conversation/chat groups
 * Can be PRIVATE (1-1) or GROUP
 * Related to: Message, ConversationMember
 */
@Document(collection = "conversations")
public class Conversations {

    @Id
    private String conversationId = UUID.randomUUID().toString();

    private ConversationType conversationType;
    private ConversationStatus conversationStatus = ConversationStatus.NORMAL;
    private List<String> participants;
    private String conversationName;
    private String avatarUrl;
    private String creatorId;
    private LocalDateTime createdAt;
    private String lastMessageId;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private String lastMessageSenderId;
    private String lastMessageSenderName;
    private Boolean isPinned;
    private String groupDescription;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
    private String autoDeleteDuration;
    private String invitationLink;
    private String aiSummary;
    private LocalDateTime aiSummaryUpdatedAt;

    public Conversations() {}

    public Conversations(String conversationId, ConversationType conversationType, ConversationStatus conversationStatus, 
                        List<String> participants, String conversationName, String avatarUrl, String creatorId, 
                        LocalDateTime createdAt, String lastMessageId, String lastMessageContent, 
                        LocalDateTime lastMessageTime, String lastMessageSenderId, String lastMessageSenderName, 
                        Boolean isPinned, String groupDescription, LocalDateTime updatedAt, Boolean isDeleted, 
                        String autoDeleteDuration, String invitationLink, String aiSummary, 
                        LocalDateTime aiSummaryUpdatedAt) {
        this.conversationId = conversationId;
        this.conversationType = conversationType;
        this.conversationStatus = conversationStatus;
        this.participants = participants;
        this.conversationName = conversationName;
        this.avatarUrl = avatarUrl;
        this.creatorId = creatorId;
        this.createdAt = createdAt;
        this.lastMessageId = lastMessageId;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageTime = lastMessageTime;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageSenderName = lastMessageSenderName;
        this.isPinned = isPinned;
        this.groupDescription = groupDescription;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
        this.autoDeleteDuration = autoDeleteDuration;
        this.invitationLink = invitationLink;
        this.aiSummary = aiSummary;
        this.aiSummaryUpdatedAt = aiSummaryUpdatedAt;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public ConversationType getConversationType() { return conversationType; }
    public void setConversationType(ConversationType conversationType) { this.conversationType = conversationType; }
    public ConversationStatus getConversationStatus() { return conversationStatus; }
    public void setConversationStatus(ConversationStatus conversationStatus) { this.conversationStatus = conversationStatus; }
    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }
    public String getConversationName() { return conversationName; }
    public void setConversationName(String conversationName) { this.conversationName = conversationName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; }
    public String getLastMessageContent() { return lastMessageContent; }
    public void setLastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }
    public String getLastMessageSenderName() { return lastMessageSenderName; }
    public void setLastMessageSenderName(String lastMessageSenderName) { this.lastMessageSenderName = lastMessageSenderName; }
    public Boolean getIsPinned() { return isPinned; }
    public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
    public String getGroupDescription() { return groupDescription; }
    public void setGroupDescription(String groupDescription) { this.groupDescription = groupDescription; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public String getAutoDeleteDuration() { return autoDeleteDuration; }
    public void setAutoDeleteDuration(String autoDeleteDuration) { this.autoDeleteDuration = autoDeleteDuration; }
    public String getInvitationLink() { return invitationLink; }
    public void setInvitationLink(String invitationLink) { this.invitationLink = invitationLink; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public LocalDateTime getAiSummaryUpdatedAt() { return aiSummaryUpdatedAt; }
    public void setAiSummaryUpdatedAt(LocalDateTime aiSummaryUpdatedAt) { this.aiSummaryUpdatedAt = aiSummaryUpdatedAt; }

    public static ConversationsBuilder builder() {
        return new ConversationsBuilder();
    }

    public static class ConversationsBuilder {
        private String conversationId = UUID.randomUUID().toString();
        private ConversationType conversationType;
        private ConversationStatus conversationStatus = ConversationStatus.NORMAL;
        private List<String> participants;
        private String conversationName;
        private String avatarUrl;
        private String creatorId;
        private LocalDateTime createdAt;
        private String lastMessageId;
        private String lastMessageContent;
        private LocalDateTime lastMessageTime;
        private String lastMessageSenderId;
        private String lastMessageSenderName;
        private Boolean isPinned;
        private String groupDescription;
        private LocalDateTime updatedAt;
        private Boolean isDeleted;
        private String autoDeleteDuration;
        private String invitationLink;
        private String aiSummary;
        private LocalDateTime aiSummaryUpdatedAt;

        public ConversationsBuilder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public ConversationsBuilder conversationType(ConversationType conversationType) { this.conversationType = conversationType; return this; }
        public ConversationsBuilder conversationStatus(ConversationStatus conversationStatus) { this.conversationStatus = conversationStatus; return this; }
        public ConversationsBuilder participants(List<String> participants) { this.participants = participants; return this; }
        public ConversationsBuilder conversationName(String conversationName) { this.conversationName = conversationName; return this; }
        public ConversationsBuilder avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
        public ConversationsBuilder creatorId(String creatorId) { this.creatorId = creatorId; return this; }
        public ConversationsBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ConversationsBuilder lastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; return this; }
        public ConversationsBuilder lastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; return this; }
        public ConversationsBuilder lastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; return this; }
        public ConversationsBuilder lastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; return this; }
        public ConversationsBuilder lastMessageSenderName(String lastMessageSenderName) { this.lastMessageSenderName = lastMessageSenderName; return this; }
        public ConversationsBuilder isPinned(Boolean isPinned) { this.isPinned = isPinned; return this; }
        public ConversationsBuilder groupDescription(String groupDescription) { this.groupDescription = groupDescription; return this; }
        public ConversationsBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public ConversationsBuilder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public ConversationsBuilder autoDeleteDuration(String autoDeleteDuration) { this.autoDeleteDuration = autoDeleteDuration; return this; }
        public ConversationsBuilder invitationLink(String invitationLink) { this.invitationLink = invitationLink; return this; }
        public ConversationsBuilder aiSummary(String aiSummary) { this.aiSummary = aiSummary; return this; }
        public ConversationsBuilder aiSummaryUpdatedAt(LocalDateTime aiSummaryUpdatedAt) { this.aiSummaryUpdatedAt = aiSummaryUpdatedAt; return this; }

        public Conversations build() {
            return new Conversations(conversationId, conversationType, conversationStatus, participants, conversationName, avatarUrl, creatorId, createdAt, lastMessageId, lastMessageContent, lastMessageTime, lastMessageSenderId, lastMessageSenderName, isPinned, groupDescription, updatedAt, isDeleted, autoDeleteDuration, invitationLink, aiSummary, aiSummaryUpdatedAt);
        }
    }
}
