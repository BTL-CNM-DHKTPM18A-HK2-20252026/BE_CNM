package iuh.fit.dto.response.friend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendSuggestionResponse {
    private String userId;
    private String fullName;
    private String username;
    private String avatarUrl;
    private int mutualFriendCount;
    private List<String> mutualFriendNames;
    private String reason;
}
