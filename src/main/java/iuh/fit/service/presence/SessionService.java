package iuh.fit.service.presence;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Layer 1 — Distributed Session Management ("The Address Book")
 *
 * <p>
 * Lưu bản đồ kết nối vật lý: userId → (serverId, socketId, tabId, lastActive).
 * Redis Hash: {@code user:session:{userId}}
 *
 * <h3>Lifecycle:</h3>
 * <ul>
 * <li><b>F5/Đóng Tab</b>: WebSocket onClose → {@link #removeSession(String)}
 * xóa Key ngay</li>
 * <li><b>Zalo-style Kick</b>: Tab mới → phát hiện phiên cũ → Pub/Sub gửi lệnh
 * kick →
 * ghi đè phiên mới vào Redis</li>
 * <li><b>Logout</b>: {@link #removeSession(String)} xóa Key ngay lập tức</li>
 * <li><b>Safety TTL</b>: 24 giờ → tự dọn zombie sessions nếu server crash</li>
 * </ul>
 *
 * <h3>Redis Structure:</h3>
 * 
 * <pre>
 * Key  : user:session:{userId}
 * Type : Hash
 * Fields:
 *   serverId   — ID instance Spring Boot đang giữ kết nối
 *   socketId   — STOMP session ID duy nhất
 *   tabId      — ID tab trình duyệt (do client gửi)
 *   lastActive — epoch millis lần cuối tương tác
 * TTL  : 24 hours (safety net)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionService.class);

    private static final String KEY_PREFIX = "user:session:";
    private static final long SESSION_TTL_HOURS = 24;

    // Hash field names
    private static final String F_SERVER_ID = "serverId";
    private static final String F_SOCKET_ID = "socketId";
    private static final String F_TAB_ID = "tabId";
    private static final String F_LAST_ACTIVE = "lastActive";

    private final StringRedisTemplate stringRedisTemplate;
    private final SessionKickPublisher sessionKickPublisher;

    /**
     * Server instance ID — unique per JVM start.
     * Dùng để biết userId đang ở server nào trong cluster.
     */
    private final String serverId = UUID.randomUUID().toString().substring(0, 8);

    // ────────────────────────────────────────────────────────────
    // Session Lifecycle
    // ────────────────────────────────────────────────────────────

    /**
     * Đăng ký phiên kết nối mới.
     *
     * <p>
     * Nếu đã tồn tại phiên cũ với tabId khác → kick phiên cũ qua Pub/Sub,
     * sau đó ghi đè phiên mới (Zalo-style single-tab).
     *
     * @param userId   ID người dùng
     * @param socketId STOMP session ID
     * @param tabId    Tab ID do client gửi (header hoặc payload)
     */
    public void registerSession(String userId, String socketId, String tabId) {
        String key = keyOf(userId);

        try {
            // Check phiên cũ (Zalo-style kick-out logic)
            Map<Object, Object> existing = stringRedisTemplate.opsForHash().entries(key);
            if (!existing.isEmpty()) {
                String oldTabId = (String) existing.get(F_TAB_ID);
                String oldSocketId = (String) existing.get(F_SOCKET_ID);
                String oldServerId = (String) existing.get(F_SERVER_ID);

                // Nếu tabId khác → kick phiên cũ
                if (oldTabId != null && !oldTabId.equals(tabId) && oldSocketId != null) {
                    log.info("[Session] Kicking old session: userId={}, oldTab={}, newTab={}", userId, oldTabId, tabId);
                    sessionKickPublisher.publishKick(userId, oldSocketId, oldServerId, oldTabId);
                }
            }

            // Ghi đè phiên mới
            stringRedisTemplate.opsForHash().put(key, F_SERVER_ID, serverId);
            stringRedisTemplate.opsForHash().put(key, F_SOCKET_ID, socketId);
            stringRedisTemplate.opsForHash().put(key, F_TAB_ID, tabId != null ? tabId : "default");
            stringRedisTemplate.opsForHash().put(key, F_LAST_ACTIVE, String.valueOf(System.currentTimeMillis()));

            // Safety TTL: 24h → dọn zombie sessions
            stringRedisTemplate.expire(key, SESSION_TTL_HOURS, TimeUnit.HOURS);

            log.info("[Session] Registered: userId={}, server={}, socket={}, tab={}",
                    userId, serverId, socketId, tabId);
        } catch (RedisConnectionFailureException ex) {
            log.warn("[Session] Redis unavailable, session not registered: {}", ex.getMessage());
        }
    }

    /**
     * Xóa phiên kết nối — gọi khi WebSocket disconnect hoặc logout.
     * Chỉ xóa nếu socketId khớp (tránh race condition khi reconnect nhanh).
     */
    public void removeSession(String userId, String socketId) {
        String key = keyOf(userId);

        try {
            String currentSocketId = (String) stringRedisTemplate.opsForHash().get(key, F_SOCKET_ID);
            // Chỉ xóa nếu socketId trùng khớp (phiên hiện tại)
            if (socketId.equals(currentSocketId)) {
                stringRedisTemplate.delete(key);
                log.info("[Session] Removed: userId={}, socket={}", userId, socketId);
            } else {
                log.debug("[Session] Skip remove — socket mismatch: current={}, removing={}",
                        currentSocketId, socketId);
            }
        } catch (RedisConnectionFailureException ex) {
            log.warn("[Session] Redis unavailable, session not removed: {}", ex.getMessage());
        }
    }

    /**
     * Xóa phiên kết nối (force) — gọi khi logout rõ ràng, không cần check socketId.
     */
    public void removeSession(String userId) {
        try {
            stringRedisTemplate.delete(keyOf(userId));
            log.info("[Session] Force removed: userId={}", userId);
        } catch (RedisConnectionFailureException ex) {
            log.warn("[Session] Redis unavailable, session not removed: {}", ex.getMessage());
        }
    }

    /**
     * Cập nhật lastActive — gọi khi nhận heartbeat hoặc message.
     */
    public void touchSession(String userId) {
        try {
            String key = keyOf(userId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                stringRedisTemplate.opsForHash().put(key, F_LAST_ACTIVE,
                        String.valueOf(System.currentTimeMillis()));
                stringRedisTemplate.expire(key, SESSION_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (RedisConnectionFailureException ex) {
            log.warn("[Session] Redis unavailable, touch failed: {}", ex.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────
    // Query APIs
    // ────────────────────────────────────────────────────────────

    /**
     * Lấy thông tin phiên của user (serverId, socketId, tabId).
     * Trả về null nếu không có phiên hoặc Redis lỗi.
     */
    public SessionInfo getSession(String userId) {
        try {
            Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(keyOf(userId));
            if (data.isEmpty())
                return null;

            return new SessionInfo(
                    (String) data.get(F_SERVER_ID),
                    (String) data.get(F_SOCKET_ID),
                    (String) data.get(F_TAB_ID),
                    parseLong((String) data.get(F_LAST_ACTIVE)));
        } catch (RedisConnectionFailureException ex) {
            log.warn("[Session] Redis unavailable, cannot get session: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Kiểm tra user có phiên đang hoạt động không.
     */
    public boolean hasActiveSession(String userId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(keyOf(userId)));
        } catch (RedisConnectionFailureException ex) {
            return false;
        }
    }

    /**
     * Lấy serverId hiện tại của instance này.
     */
    public String getServerId() {
        return serverId;
    }

    // ────────────────────────────────────────────────────────────
    // Internal
    // ────────────────────────────────────────────────────────────

    private String keyOf(String userId) {
        return KEY_PREFIX + userId;
    }

    private long parseLong(String value) {
        try {
            return value != null ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Immutable session info record.
     */
    public record SessionInfo(String serverId, String socketId, String tabId, long lastActive) {
    }
}
