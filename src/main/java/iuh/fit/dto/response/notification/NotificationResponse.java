package iuh.fit.dto.response.notification;

import java.time.LocalDateTime;
import java.util.Map;

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

    // Actor (snapshot)
    private String actorId;
    private String actorName;
    private String actorAvatarUrl;

    private String notificationType;
    private String title;
    private String body;
    private String iconUrl;
    private String deepLink;

    // Object & target
    private String objectType;
    private String objectId;
    private String targetType;
    private String targetId;
    private String relatedObjectId; // backward-compat = objectId

    // Aggregation / state
    private String groupKey;
    private Integer aggregateCount;
    private String actionStatus;
    private Map<String, Object> metadata;

    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
