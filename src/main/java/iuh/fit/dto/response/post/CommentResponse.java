package iuh.fit.dto.response.post;

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
public class CommentResponse {
    private String commentId;
    private String postId;
    private String userId;
    private String userName;
    private String userAvatarUrl;
    private String content;
    private String parentCommentId;
    private Integer likeCount;
    private List<CommentResponse> replies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
