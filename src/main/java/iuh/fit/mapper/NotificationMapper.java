package iuh.fit.mapper;

import org.springframework.stereotype.Component;

import iuh.fit.dto.response.notification.NotificationResponse;
import iuh.fit.entity.Notification;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        if (n == null)
            return null;

        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .receiverId(n.getReceiverId())
                .actorId(n.getActorId())
                .actorName(n.getActorName())
                .actorAvatarUrl(n.getActorAvatarUrl())
                .notificationType(n.getNotificationType() != null ? n.getNotificationType().name() : null)
                .title(n.getTitle())
                .body(n.getBody())
                .iconUrl(n.getIconUrl())
                .deepLink(n.getDeepLink())
                .objectType(n.getObjectType())
                .objectId(n.getObjectId())
                .targetType(n.getTargetType())
                .targetId(n.getTargetId())
                .relatedObjectId(n.getObjectId() != null ? n.getObjectId() : n.getEntityId())
                .groupKey(n.getGroupKey())
                .aggregateCount(n.getAggregateCount())
                .actionStatus(n.getActionStatus())
                .metadata(n.getMetadata())
                .isRead(Boolean.TRUE.equals(n.getIsRead()))
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
