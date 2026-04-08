package iuh.fit.dto.response.user;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * DTO broadcast qua STOMP khi trạng thái online/offline thay đổi.
 * Client nhận message này từ topic: /topic/presence/{userId}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserStatusDTO {

    /** ID của user thay đổi trạng thái */
    String userId;

    /** true = đang online, false = vừa offline */
    boolean online;

    /**
     * Thời gian cuối cùng user online (chỉ có giá trị khi online = false).
     * Client dùng giá trị này để hiển thị "Hoạt động 5 phút trước".
     */
    LocalDateTime lastSeen;
}
