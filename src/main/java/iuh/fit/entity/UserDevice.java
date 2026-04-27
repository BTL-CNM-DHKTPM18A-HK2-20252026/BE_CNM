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

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String id) { this.deviceId = id; }
    public String getUserId() { return userId; }
    public void setUserId(String id) { this.userId = id; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String name) { this.deviceName = name; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String type) { this.deviceType = type; }
    public String getBrowser() { return browser; }
    public void setBrowser(String b) { this.browser = b; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ip) { this.ipAddress = ip; }
    public LocalDateTime getLoginAt() { return loginAt; }
    public void setLoginAt(LocalDateTime time) { this.loginAt = time; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime time) { this.lastActiveAt = time; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime time) { this.createdAt = time; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { this.isActive = active; }

    public static UserDeviceBuilder builder() {
        return new UserDeviceBuilder();
    }

    public static class UserDeviceBuilder {
        private final UserDevice device = new UserDevice();

        public UserDeviceBuilder userId(String id) { device.setUserId(id); return this; }
        public UserDeviceBuilder deviceName(String name) { device.setDeviceName(name); return this; }
        public UserDeviceBuilder deviceType(String type) { device.setDeviceType(type); return this; }
        public UserDeviceBuilder browser(String b) { device.setBrowser(b); return this; }
        public UserDeviceBuilder os(String os) { device.setOs(os); return this; }
        public UserDeviceBuilder ipAddress(String ip) { device.setIpAddress(ip); return this; }
        public UserDeviceBuilder loginAt(LocalDateTime time) { device.setLoginAt(time); return this; }
        public UserDeviceBuilder lastActiveAt(LocalDateTime time) { device.setLastActiveAt(time); return this; }
        public UserDeviceBuilder createdAt(LocalDateTime time) { device.setCreatedAt(time); return this; }
        public UserDeviceBuilder isActive(Boolean active) { device.setIsActive(active); return this; }
        public UserDevice build() { return device; }
    }
}
