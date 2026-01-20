package iuh.fit.dto.response.post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private String postId;
    private String authorId;
    private String authorName;
    private String authorAvatarUrl;
    private String content;
    private String location;
    private List<String> mediaUrls;
    private List<String> taggedUsers;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Map<String, Integer> reactionCounts; // {LIKE: 10, LOVE: 5, ...}
    private Boolean isLikedByMe;
    private String myReactionType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
