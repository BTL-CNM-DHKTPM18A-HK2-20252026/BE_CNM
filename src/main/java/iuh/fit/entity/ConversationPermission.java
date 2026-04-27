package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * ConversationPermission entity - Stores group settings and permissions
 * Related to: Conversations (conversationId)
 */
@Document(collection = "conversation_permissions")
public class ConversationPermission {

    @Id
    private String id = UUID.randomUUID().toString();

    private String conversationId; 
    private Boolean canEditInfo = true; 
    private Boolean canPinMessages = true;
    private Boolean canCreateNotes = true;
    private Boolean canCreatePolls = true;
    private Boolean canSendMessages = true;
    private Boolean isMemberApprovalRequired = false;
    private Boolean isHighlightAdminMessages = true;
    private Boolean canNewMembersReadRecentMessages = true;
    private LocalDateTime updatedAt;

    public ConversationPermission() {}

    public ConversationPermission(String id, String conversationId, Boolean canEditInfo, Boolean canPinMessages, 
                                Boolean canCreateNotes, Boolean canCreatePolls, Boolean canSendMessages, 
                                Boolean isMemberApprovalRequired, Boolean isHighlightAdminMessages, 
                                Boolean canNewMembersReadRecentMessages, LocalDateTime updatedAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.canEditInfo = canEditInfo;
        this.canPinMessages = canPinMessages;
        this.canCreateNotes = canCreateNotes;
        this.canCreatePolls = canCreatePolls;
        this.canSendMessages = canSendMessages;
        this.isMemberApprovalRequired = isMemberApprovalRequired;
        this.isHighlightAdminMessages = isHighlightAdminMessages;
        this.canNewMembersReadRecentMessages = canNewMembersReadRecentMessages;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Boolean getCanEditInfo() { return canEditInfo; }
    public void setCanEditInfo(Boolean canEditInfo) { this.canEditInfo = canEditInfo; }
    public Boolean getCanPinMessages() { return canPinMessages; }
    public void setCanPinMessages(Boolean canPinMessages) { this.canPinMessages = canPinMessages; }
    public Boolean getCanCreateNotes() { return canCreateNotes; }
    public void setCanCreateNotes(Boolean canCreateNotes) { this.canCreateNotes = canCreateNotes; }
    public Boolean getCanCreatePolls() { return canCreatePolls; }
    public void setCanCreatePolls(Boolean canCreatePolls) { this.canCreatePolls = canCreatePolls; }
    public Boolean getCanSendMessages() { return canSendMessages; }
    public void setCanSendMessages(Boolean canSendMessages) { this.canSendMessages = canSendMessages; }
    public Boolean getIsMemberApprovalRequired() { return isMemberApprovalRequired; }
    public void setIsMemberApprovalRequired(Boolean isMemberApprovalRequired) { this.isMemberApprovalRequired = isMemberApprovalRequired; }
    public Boolean getIsHighlightAdminMessages() { return isHighlightAdminMessages; }
    public void setIsHighlightAdminMessages(Boolean isHighlightAdminMessages) { this.isHighlightAdminMessages = isHighlightAdminMessages; }
    public Boolean getCanNewMembersReadRecentMessages() { return canNewMembersReadRecentMessages; }
    public void setCanNewMembersReadRecentMessages(Boolean canNewMembersReadRecentMessages) { this.canNewMembersReadRecentMessages = canNewMembersReadRecentMessages; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static ConversationPermissionBuilder builder() {
        return new ConversationPermissionBuilder();
    }

    public static class ConversationPermissionBuilder {
        private String id = UUID.randomUUID().toString();
        private String conversationId;
        private Boolean canEditInfo = true;
        private Boolean canPinMessages = true;
        private Boolean canCreateNotes = true;
        private Boolean canCreatePolls = true;
        private Boolean canSendMessages = true;
        private Boolean isMemberApprovalRequired = false;
        private Boolean isHighlightAdminMessages = true;
        private Boolean canNewMembersReadRecentMessages = true;
        private LocalDateTime updatedAt;

        public ConversationPermissionBuilder id(String id) { this.id = id; return this; }
        public ConversationPermissionBuilder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public ConversationPermissionBuilder canEditInfo(Boolean canEditInfo) { this.canEditInfo = canEditInfo; return this; }
        public ConversationPermissionBuilder canPinMessages(Boolean canPinMessages) { this.canPinMessages = canPinMessages; return this; }
        public ConversationPermissionBuilder canCreateNotes(Boolean canCreateNotes) { this.canCreateNotes = canCreateNotes; return this; }
        public ConversationPermissionBuilder canCreatePolls(Boolean canCreatePolls) { this.canCreatePolls = canCreatePolls; return this; }
        public ConversationPermissionBuilder canSendMessages(Boolean canSendMessages) { this.canSendMessages = canSendMessages; return this; }
        public ConversationPermissionBuilder isMemberApprovalRequired(Boolean isMemberApprovalRequired) { this.isMemberApprovalRequired = isMemberApprovalRequired; return this; }
        public ConversationPermissionBuilder isHighlightAdminMessages(Boolean isHighlightAdminMessages) { this.isHighlightAdminMessages = isHighlightAdminMessages; return this; }
        public ConversationPermissionBuilder canNewMembersReadRecentMessages(Boolean canNewMembersReadRecentMessages) { this.canNewMembersReadRecentMessages = canNewMembersReadRecentMessages; return this; }
        public ConversationPermissionBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public ConversationPermission build() {
            return new ConversationPermission(id, conversationId, canEditInfo, canPinMessages, canCreateNotes, canCreatePolls, canSendMessages, isMemberApprovalRequired, isHighlightAdminMessages, canNewMembersReadRecentMessages, updatedAt);
        }
    }
}
