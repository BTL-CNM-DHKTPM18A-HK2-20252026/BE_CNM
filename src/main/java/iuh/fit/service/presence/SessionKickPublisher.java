package iuh.fit.service.presence;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pub/Sub Publisher — gửi lệnh kick phiên cũ qua Redis channel
 * {@code session:kick}.
 *
 * <p>
 * Message format: {@code userId|socketId|targetServerId}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionKickPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ChannelTopic sessionKickTopic;

    /**
     * Publish lệnh kick tới tất cả server instances đang subscribe.
     *
     * @param userId         ID user cần kick
     * @param socketId       Socket ID của phiên cũ cần đóng
     * @param targetServerId Server ID đang giữ phiên cũ (để server khớp mới xử lý)
     * @param tabId          Tab ID của phiên cũ (để client xác định đúng tab bị
     *                       kick)
     */
    public void publishKick(String userId, String socketId, String targetServerId, String tabId) {
        String message = userId + "|" + socketId + "|" + targetServerId + "|" + tabId;
        try {
            stringRedisTemplate.convertAndSend(sessionKickTopic.getTopic(), message);
            log.info("[SessionKick] Published kick: userId={}, socket={}, targetServer={}",
                    userId, socketId, targetServerId);
        } catch (RedisConnectionFailureException ex) {
            log.warn("[SessionKick] Redis unavailable, kick not published: {}", ex.getMessage());
        }
    }
}
