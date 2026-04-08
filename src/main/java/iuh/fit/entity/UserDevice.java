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
 * UserDevice entity - Stores user device information for login tracking and
 * push notifications
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
    String deviceName; // e.g. "Chrome on Windows 10"
    String deviceType; // WEB, MOBILE, DESKTOP
    String browser; // Chrome, Firefox, Safari, Edge, etc.
    String os; // Windows, macOS, Android, iOS, Linux
    String ipAddress;
    String fcmToken; // Firebase Cloud Messaging token
    String authTokenHash;
    LocalDateTime loginAt;
    LocalDateTime lastActiveAt;
    LocalDateTime createdAt;

    @Builder.Default
    Boolean isActive = true;
}
