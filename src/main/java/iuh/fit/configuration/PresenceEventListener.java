package iuh.fit.configuration;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import iuh.fit.service.presence.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PresenceEventListener — lắng nghe các sự kiện kết nối / ngắt kết nối
 * WebSocket của Spring STOMP để cập nhật trạng thái online của user.
 *
 * <p>
 * Luồng:
 * <ol>
 * <li>{@code SessionConnectEvent} → Lấy userId từ Principal (được set bởi
 * {@link WebSocketAuthInterceptor}) → gọi
 * {@code presenceService.userConnected()}</li>
 * <li>{@code SessionDisconnectEvent} → gọi
 * {@code presenceService.userDisconnected()}</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final PresenceService presenceService;

    /**
     * Được gọi khi một client STOMP gửi frame CONNECT thành công.
     */
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();

        if (user != null) {
            String userId = user.getName();
            log.info("[WS-Event] SESSION_CONNECT — userId={}, sessionId={}",
                    userId, accessor.getSessionId());
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
            log.info("[WS-Event] SESSION_DISCONNECT — userId={}, sessionId={}",
                    userId, accessor.getSessionId());
            presenceService.userDisconnected(userId);
        } else {
            log.warn("[WS-Event] SESSION_DISCONNECT without Principal — sessionId={}",
                    accessor.getSessionId());
        }
    }
}
