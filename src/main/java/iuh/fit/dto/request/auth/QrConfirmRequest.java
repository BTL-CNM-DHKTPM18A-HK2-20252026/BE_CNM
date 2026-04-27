package iuh.fit.dto.request.auth;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * QR Confirm Request
 * Used by mobile app to confirm QR login
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QrConfirmRequest {
    String uuid;
    String userId; // Identity of the mobile user

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
