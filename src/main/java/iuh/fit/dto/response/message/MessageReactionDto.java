package iuh.fit.dto.response.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import iuh.fit.enums.ReactionType;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReactionDto {
    private String id;
    private String userId;
    private String userName;
    private String userAvatar;
    private ReactionType icon;
    private LocalDateTime createdAt;
}
