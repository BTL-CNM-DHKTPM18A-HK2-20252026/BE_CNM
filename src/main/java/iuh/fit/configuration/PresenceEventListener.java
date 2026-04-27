package iuh.fit.configuration;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import iuh.fit.service.presence.PresenceService;
import iuh.fit.service.presence.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PresenceEventListener — lắng nghe các sự kiện kết nối / ngắt kết nối
 * WebSocket của Spring STOMP để cập nhật cả 2 layer:
 *
 * <ul>
 * <li><b>Layer 1</b>: {@link SessionService} — bản đồ kết nối phân tán</li>
 * <li><b>Layer 2</b>: {@link PresenceService} — chấm xanh online/offline</li>
 * </ul>
 *
 * <h3>Luồng CONNECT:</h3>
 * <ol>
 * <li>Lấy userId từ Principal (set bởi {@link WebSocketAuthInterceptor})</li>
 * <li>Lấy tabId từ STOMP header {@code X-Tab-Id} (do client gửi)</li>
 * <li>SessionService: đăng ký phiên → kick phiên cũ nếu tabId khác
 * (Zalo-style)</li>
 * <li>PresenceService: đánh dấu online + broadcast tới bạn bè</li>
 * </ol>
 *
 * <h3>Luồng DISCONNECT:</h3>
 * <ol>
 * <li>SessionService: xóa phiên (chỉ khi socketId khớp)</li>
 * <li>PresenceService: xóa trạng thái online + broadcast offline</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PresenceEventListener.class);

    private final PresenceService presenceService;
    private final SessionService sessionService;

    /**
     * Được gọi khi một client STOMP gửi frame CONNECT thành công.
     */
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();

        if (user != null) {
            String userId = user.getName();
            String socketId = accessor.getSessionId();
            // Client gửi tabId qua STOMP header: X-Tab-Id
            String tabId = accessor.getFirstNativeHeader("X-Tab-Id");

            log.info("[WS-Event] SESSION_CONNECT — userId={}, sessionId={}, tabId={}",
                    userId, socketId, tabId);

            // Layer 1: Register session → kick old tab if different (Zalo-style)
            sessionService.registerSession(userId, socketId, tabId);

            // Layer 2: Mark online + broadcast to friends
            presenceService.userConnected(userId);
        } else {
            log.warn("[WS-Event] SESSION_CONNECT without Principal — sessionId={}",
                    accessor.getSessionId());
        }
    }

    /**
     * Được gọi khi session WebSocket bị đóng (user tắt tab, mất mạng, logout…).
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();

        if (user != null) {
            String userId = user.getName();
            String socketId = accessor.getSessionId();

            log.info("[WS-Event] SESSION_DISCONNECT — userId={}, sessionId={}",
                    userId, socketId);

            // Layer 1: Remove session (chỉ khi socketId khớp — tránh race condition)
            sessionService.removeSession(userId, socketId);

            // Layer 2: Mark offline + broadcast + persist lastSeen
            presenceService.userDisconnected(userId);
        } else {
            log.warn("[WS-Event] SESSION_DISCONNECT without Principal — sessionId={}",
                    accessor.getSessionId());
        }
    }
}
