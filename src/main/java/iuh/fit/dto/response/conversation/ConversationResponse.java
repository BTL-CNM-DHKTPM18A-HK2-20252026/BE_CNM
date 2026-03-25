package iuh.fit.dto.response.conversation;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponse {
    private String conversationId;
    private String conversationType;
    private String conversationName;
    private String conversationAvatarUrl;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private Boolean isPinned;
    private List<MemberInfo> members;
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MemberInfo {
        private String userId;
        private String displayName;
        private String avatarUrl;
        private String role;
    }
}
