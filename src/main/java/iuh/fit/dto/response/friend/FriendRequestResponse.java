package iuh.fit.dto.response.friend;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendRequestResponse {
    private String requestId;
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String receiverId;
    private String receiverName;
    private String receiverAvatarUrl;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
