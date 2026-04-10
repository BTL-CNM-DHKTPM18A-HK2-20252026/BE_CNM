package iuh.fit.response;

import java.util.List;

import org.springframework.data.domain.Page;

import iuh.fit.document.DocumentDocument;
import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import iuh.fit.dto.response.conversation.ConversationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResult {
    private List<FriendSearchItem> friends;
    private List<ConversationSearchItem> conversations;
    private Page<SearchResult<MessageDocument>> messages;
    private List<FriendSearchItem> globalUsers;

    // Legacy fields kept for backward compatibility
    private Page<SearchResult<UserDocument>> users;
    private Page<SearchResult<DocumentDocument>> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendSearchItem {
        private String userId;
        private String displayName;
        private String phoneNumber;
        private String avatarUrl;
        private String friendshipStatus; // ACCEPTED, PENDING, NONE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationSearchItem {
        private String conversationId;
        private String conversationType;
        private String conversationName;
        private String conversationAvatarUrl;
        private String lastMessageContent;
        private String lastMessageTime;
    }
}
