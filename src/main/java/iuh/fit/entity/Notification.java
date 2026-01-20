package iuh.fit.entity;

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
    String notificationId;
    
    String userId; // Reference to UserAuth (who receives the notification)
    String actorId; // Reference to UserAuth (who triggered the notification)
    String entityId; // Reference to related entity (Post, FriendRequest, etc.)
    NotificationType type; // FRIEND_REQ, LIKE_POST, etc.
    Boolean isRead;
}
