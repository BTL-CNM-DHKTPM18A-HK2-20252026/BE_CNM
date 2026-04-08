package iuh.fit.service.presence;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pub/Sub Subscriber — nhận lệnh kick từ channel {@code session:kick}.
 *
 * <p>
 * Khi nhận message, kiểm tra targetServerId có khớp với server hiện tại không.
 * Nếu khớp → gửi lệnh "SESSION_KICKED" tới client qua STOMP user queue
 * để client tự disconnect.
 *
 * <p>
 * Message format: {@code userId|socketId|targetServerId}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionKickSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionService sessionService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody());
        String[] parts = payload.split("\\|", 3);

        if (parts.length < 3) {
            log.warn("[SessionKick] Invalid message format: {}", payload);
            return;
        }

        String userId = parts[0];
        String socketId = parts[1];
        String targetServerId = parts[2];

        // Chỉ xử lý nếu message nhắm tới server này
        if (!sessionService.getServerId().equals(targetServerId)) {
            log.debug("[SessionKick] Ignoring kick for server={}, I am={}",
                    targetServerId, sessionService.getServerId());
            return;
        }

        log.info("[SessionKick] Executing kick: userId={}, socket={}", userId, socketId);

        // Gửi lệnh kick tới client cụ thể qua STOMP user destination
        // Client nhận từ: /user/queue/session-kick
        java.util.Map<String, Object> kickPayload = new java.util.HashMap<>();
        kickPayload.put("type", "SESSION_KICKED");
        kickPayload.put("reason", "Tài khoản đã đăng nhập ở thiết bị/tab khác");
        kickPayload.put("socketId", socketId);

        messagingTemplate.convertAndSendToUser(userId, "/queue/session-kick", kickPayload);
    }
}
