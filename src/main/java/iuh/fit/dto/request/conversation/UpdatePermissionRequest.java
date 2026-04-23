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
}
