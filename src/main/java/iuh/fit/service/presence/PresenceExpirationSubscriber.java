package iuh.fit.service.presence;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Keyspace Notification Subscriber — lắng nghe sự kiện key expired
 * của Redis trên pattern {@code __keyevent@*__:expired}.
 *
 * <p>
 * Khi key {@code user:presence:{userId}} hết TTL (do client ngắt kết nối
 * đột ngột mà không có WebSocket close frame), subscriber này sẽ broadcast
 * trạng thái offline tới bạn bè của user đó.
 *
 * <h3>Luồng xử lý:</h3>
 * <ol>
 * <li>Redis key {@code user:presence:{userId}} hết TTL (60s)</li>
 * <li>Redis publish tới {@code __keyevent@0__:expired} với body = tên key</li>
 * <li>Subscriber nhận → trích xuất userId → gọi
 * {@link PresenceService#userDisconnected}</li>
 * <li>PresenceService broadcast OFFLINE tới bạn bè qua STOMP</li>
 * </ol>
 *
 * <p>
 * <b>Note:</b> Nếu user đã disconnect đúng cách (qua WebSocket close),
 * {@link PresenceService#userDisconnected} đã được gọi và key đã bị xóa
 * trước khi expire — subscriber sẽ không nhận notification đó.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceExpirationSubscriber implements MessageListener {

    private static final String PRESENCE_KEY_PREFIX = "user:presence:";

    private final PresenceService presenceService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());

        // Chỉ xử lý các key presence, bỏ qua session keys và các key khác
        if (!expiredKey.startsWith(PRESENCE_KEY_PREFIX)) {
            return;
        }

        String userId = expiredKey.substring(PRESENCE_KEY_PREFIX.length());
        log.info("[PresenceExpiry] Key expired for userId={} — broadcasting offline", userId);

        // Gọi userDisconnected để:
        // 1. Xóa local cache (no-op nếu đã xóa)
        // 2. Cập nhật lastSeen trong MongoDB
        // 3. Broadcast OFFLINE tới bạn bè qua STOMP
        presenceService.userDisconnected(userId);
    }
}
