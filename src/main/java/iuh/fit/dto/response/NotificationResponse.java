package iuh.fit.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private String notificationId;
    private String receiverId;
    private String actorId;
    private String actorName;
    private String actorAvatarUrl;
    private String notificationType;
    private String content;
    private String relatedObjectId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
