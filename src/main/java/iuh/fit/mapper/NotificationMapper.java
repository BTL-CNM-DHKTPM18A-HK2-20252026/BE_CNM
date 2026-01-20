package iuh.fit.mapper;

import iuh.fit.dto.response.notification.NotificationResponse;
import iuh.fit.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {
    
    public NotificationResponse toResponse(Notification notification) {
        if (notification == null) {
            return null;
        }
        
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .receiverId(notification.getReceiverId())
                .actorId(notification.getActorId())
                .notificationType(notification.getNotificationType() != null ? 
                        notification.getNotificationType().toString() : null)
                .content(notification.getContent())
                .relatedObjectId(notification.getRelatedObjectId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
