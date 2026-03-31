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
    private LocalDateTime pinnedAt;
    private List<MemberInfo> members;
    private LocalDateTime createdAt;
    private String conversationStatus; // NORMAL, PENDING, BLOCKED
    private String conversationTag; // Per-user tag: customer, family, work, friends, reply_later, colleagues
    private String groupDescription;
    private LocalDateTime mutedUntil; // Null = not muted
    private Boolean isMarkedUnread; // Per-user mark as unread
    private String autoDeleteDuration; // off, 1d, 7d, 30d

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MemberInfo {
        private String userId;
        private String displayName;
        private String avatarUrl;
        private String role;
        private String nickname;
    }
}
