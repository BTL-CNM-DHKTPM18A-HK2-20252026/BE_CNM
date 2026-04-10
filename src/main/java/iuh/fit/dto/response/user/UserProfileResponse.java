package iuh.fit.dto.response.user;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {
    private String userId;
    private String displayName;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private String coverPhotoUrl;
    private String bio;
    private String gmail;
    private String phoneNumber;
    private Integer friendCount;
    private Boolean isFriend;
    private Boolean hasPendingRequest;
    private Boolean isBlocked;
    private LocalDateTime createdAt;
}
