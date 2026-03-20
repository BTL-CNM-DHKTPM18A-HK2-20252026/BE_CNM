package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * UserDevice entity - Stores user device information for push notifications
 * Related to UserAuth (userId)
 */
@Document(collection = "user_device")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDevice {
    
    @Id
    @Builder.Default
    String deviceId = UUID.randomUUID().toString();
    
    String userId; // Reference to UserAuth
    String deviceName;
    String deviceType; // iOS, Android, Web
    String deviceOs;
    String fcmToken; // Firebase Cloud Messaging token
    String authTokenHash;
    LocalDateTime lastActiveAt;
    LocalDateTime createdAt;
    Boolean isActive;
}
