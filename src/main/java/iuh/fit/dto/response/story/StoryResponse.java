package iuh.fit.dto.response.story;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryResponse {
    private String storyId;
    private String authorId;
    private String authorName;
    private String authorAvatarUrl;
    private String mediaUrl;
    private String mediaType;
    private String caption;
    private String background;
    private Integer viewCount;
    private Boolean isViewedByMe;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
