package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.NotificationType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Notification entity - Stores user notifications
 * Related to: UserAuth (userId, actorId)
 * References entities based on entityId and type
 */
@Document(collection = "notification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {
    
    @Id
    @Builder.Default
    String notificationId = UUID.randomUUID().toString();

    String receiverId; // Reference to UserAuth (who receives the notification)
    String actorId; // Reference to UserAuth (who triggered the notification)
    String entityId; // Reference to related entity (Post, FriendRequest, etc.)
    NotificationType notificationType; // FRIEND_REQ, LIKE_POST, etc.
    Boolean isRead;
    LocalDateTime createdAt;
    Boolean isDeleted;
}
