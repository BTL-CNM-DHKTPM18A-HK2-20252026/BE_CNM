package iuh.fit.service.notification;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import iuh.fit.dto.response.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lắng nghe Redis Pub/Sub channel "notif:{userId}" rồi forward qua STOMP
 * tới client đang subscribe "/topic/notifications/{userId}".
 *
 * Lý do dùng Pub/Sub: cho phép nhiều BE instance, chỉ instance nào giữ phiên
 * STOMP của user mới đẩy thực sự — các instance khác cũng nhận message nhưng
 * SimpMessagingTemplate broadcast về broker chung nên an toàn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher implements MessageListener {

    public static final String CHANNEL_PREFIX = "notif:";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            if (!channel.startsWith(CHANNEL_PREFIX))
                return;

            String userId = channel.substring(CHANNEL_PREFIX.length());
            String body = new String(message.getBody());
            NotificationResponse payload = objectMapper.readValue(body, NotificationResponse.class);

            String dest = "/topic/notifications/" + userId;
            messagingTemplate.convertAndSend(dest, payload);
            log.debug("[Notif] Dispatched to {} (id={}, type={})",
                    dest, payload.getNotificationId(), payload.getNotificationType());
        } catch (Exception e) {
            log.warn("[Notif] Dispatcher failed: {}", e.getMessage());
        }
    }
}
