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
public class StoryViewerResponse {
    private String userId;
    private String displayName;
    private String avatarUrl;
    private LocalDateTime viewedAt;
    private String reaction;
}
