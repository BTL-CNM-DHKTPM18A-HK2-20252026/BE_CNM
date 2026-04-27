package iuh.fit.dto.request.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePermissionRequest {
    private Boolean canEditInfo;
    private Boolean canPinMessages;
    private Boolean canCreateNotes;
    private Boolean canCreatePolls;
    private Boolean canSendMessages;
    private Boolean isMemberApprovalRequired;
    private Boolean isHighlightAdminMessages;
    private Boolean canNewMembersReadRecentMessages;

    // Manual getters to bypass Lombok processing issues on some environments
    public Boolean getCanEditInfo() { return canEditInfo; }
    public Boolean getCanPinMessages() { return canPinMessages; }
    public Boolean getCanCreateNotes() { return canCreateNotes; }
    public Boolean getCanCreatePolls() { return canCreatePolls; }
    public Boolean getCanSendMessages() { return canSendMessages; }
    public Boolean getIsMemberApprovalRequired() { return isMemberApprovalRequired; }
    public Boolean getIsHighlightAdminMessages() { return isHighlightAdminMessages; }
    public Boolean getCanNewMembersReadRecentMessages() { return canNewMembersReadRecentMessages; }
}
