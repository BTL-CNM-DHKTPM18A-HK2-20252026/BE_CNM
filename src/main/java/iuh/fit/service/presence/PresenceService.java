package iuh.fit.service.presence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import iuh.fit.dto.response.user.UserStatusDTO;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserSetting;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Layer 2 — Real-time Presence ("The Green Dot")
 *
 * <p>
 * Cơ chế Heartbeat: Client gửi Ping mỗi 30s → Server SET key với TTL 60s.
 * Nếu quá 60s không có Ping → Redis tự xóa key → User hiện Offline.
 *
 * <h3>Redis Structure:</h3>
 * 
 * <pre>
 * Key   : user:presence:{userId}
 * Type  : String
 * Value : "online"
 * TTL   : 60 seconds
 * </pre>
 *
 * <p>
 * Kiểm tra online: {@code EXISTS user:presence:{userId}} → O(1), &lt;0.1ms
 *
 * <h3>Fallback:</h3>
 * Nếu Redis bị sập → fallback sang ConcurrentHashMap in-memory.
 * Không ảnh hưởng logic gửi tin nhắn chính (Kafka → MongoDB).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private static final String REDIS_PREFIX = "user:presence:";
    /** TTL dài hơn heartbeat interval (30s) để tránh flicker */
    private static final long PRESENCE_TTL_SECONDS = 60;
    private static final long LOCAL_PRESENCE_TTL_MILLIS = PRESENCE_TTL_SECONDS * 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final FriendshipRepository friendshipRepository;
    private final UserAuthRepository userAuthRepository;
    private final UserSettingRepository userSettingRepository;

    // Fallback cache when Redis is down: userId -> expiresAtEpochMillis
    private final ConcurrentMap<String, Long> localPresenceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean redisHealthy = new AtomicBoolean(true);

    // ────────────────────────────────────────────────────────────
    // Core lifecycle
    // ────────────────────────────────────────────────────────────

    /**
     * Đánh dấu user online trong Redis và broadcast tới bạn bè.
     */
    public void userConnected(String userId) {
        markOnline(userId);
        log.info("[Presence] User {} connected", userId);

        broadcastStatus(userId, true);
    }

    /**
     * Xóa trạng thái online, cập nhật lastSeen, broadcast offline.
     */
    public void userDisconnected(String userId) {
        markOffline(userId);

        // Persist lastSeen vào MongoDB
        LocalDateTime now = LocalDateTime.now();
        userAuthRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(now); // Tận dụng field lastLoginAt làm lastSeen
            userAuthRepository.save(user);
        });

        log.info("[Presence] User {} disconnected – lastSeen = {}", userId, now);

        broadcastStatus(userId, false);
    }

    /**
     * Client gửi heartbeat định kỳ (mỗi 25-30s) để renew TTL.
     * Nếu client "chết" mà không disconnect, key sẽ tự expire → offline.
     */
    public void heartbeat(String userId) {
        markOnline(userId);
    }

    // ────────────────────────────────────────────────────────────
    // Query APIs
    // ────────────────────────────────────────────────────────────

    /**
     * Kiểm tra một user có đang online không (từ Redis — O(1)).
     */
    public boolean isOnline(String userId) {
        return isOnlineInternal(userId);
    }

    /**
     * Lấy trạng thái online của nhiều userIds cùng lúc.
     * Trả về Map&lt;userId, isOnline&gt;.
     */
    public Map<String, Boolean> getOnlineStatuses(List<String> userIds) {
        return userIds.stream().collect(Collectors.toMap(
                id -> id,
                this::isOnline));
    }

    private void markOnline(String userId) {
        localPresenceCache.put(userId, System.currentTimeMillis() + LOCAL_PRESENCE_TTL_MILLIS);
        try {
            stringRedisTemplate.opsForValue().set(
                    REDIS_PREFIX + userId, "online", PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
            if (!redisHealthy.getAndSet(true)) {
                log.info("[Presence] Redis recovered, presence writes restored.");
            }
        } catch (RedisConnectionFailureException ex) {
            onRedisFailure(ex);
        } catch (RuntimeException ex) {
            onRedisFailure(ex);
        }
    }

    private void markOffline(String userId) {
        localPresenceCache.remove(userId);
        try {
            stringRedisTemplate.delete(REDIS_PREFIX + userId);
            if (!redisHealthy.getAndSet(true)) {
                log.info("[Presence] Redis recovered, presence delete restored.");
            }
        } catch (RedisConnectionFailureException ex) {
            onRedisFailure(ex);
        } catch (RuntimeException ex) {
            onRedisFailure(ex);
        }
    }

    private boolean isOnlineInternal(String userId) {
        try {
            boolean online = Boolean.TRUE.equals(stringRedisTemplate.hasKey(REDIS_PREFIX + userId));
            if (!redisHealthy.getAndSet(true)) {
                log.info("[Presence] Redis recovered, presence reads restored.");
            }
            return online;
        } catch (RedisConnectionFailureException ex) {
            onRedisFailure(ex);
            return isOnlineFromLocalCache(userId);
        } catch (RuntimeException ex) {
            onRedisFailure(ex);
            return isOnlineFromLocalCache(userId);
        }
    }

    private boolean isOnlineFromLocalCache(String userId) {
        Long expiresAt = localPresenceCache.get(userId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            localPresenceCache.remove(userId);
            return false;
        }
        return true;
    }

    private void onRedisFailure(Exception ex) {
        if (redisHealthy.getAndSet(false)) {
            log.warn("[Presence] Redis unavailable, switching to in-memory presence fallback: {}", ex.getMessage());
        }
    }

    /**
     * Lấy danh sách bạn bè đang online của một user.
     */
    public List<UserStatusDTO> getFriendsStatus(String userId) {
        List<String> friendIds = getFriendIds(userId);
        List<UserStatusDTO> result = new ArrayList<>();

        for (String fid : friendIds) {
            // Kiểm tra privacy — nếu bạn ẩn status thì không trả về
            if (isStatusHidden(fid))
                continue;

            boolean online = isOnline(fid);
            LocalDateTime lastSeen = null;
            if (!online) {
                lastSeen = userAuthRepository.findById(fid)
                        .map(UserAuth::getLastLoginAt)
                        .orElse(null);
            }
            result.add(UserStatusDTO.builder()
                    .userId(fid)
                    .online(online)
                    .lastSeen(lastSeen)
                    .build());
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────
    // Broadcast helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Gửi cập nhật trạng thái tới bạn bè qua STOMP topic.
     * Nếu user đã bật {@code showOnlineStatus = false} thì KHÔNG broadcast.
     */
    private void broadcastStatus(String userId, boolean online) {
        if (isStatusHidden(userId)) {
            log.debug("[Presence] User {} has hidden status — skip broadcast", userId);
            return;
        }

        LocalDateTime lastSeen = null;
        if (!online) {
            lastSeen = userAuthRepository.findById(userId)
                    .map(UserAuth::getLastLoginAt)
                    .orElse(null);
        }

        UserStatusDTO payload = UserStatusDTO.builder()
                .userId(userId)
                .online(online)
                .lastSeen(lastSeen)
                .build();

        // Broadcast tới từng bạn bè đang listen
        List<String> friendIds = getFriendIds(userId);
        for (String fid : friendIds) {
            messagingTemplate.convertAndSend("/topic/presence/" + fid, payload);
        }
        log.debug("[Presence] Broadcast {} to {} friends", online ? "ONLINE" : "OFFLINE", friendIds.size());
    }

    // ────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách ID bạn bè đã ACCEPTED.
     */
    private List<String> getFriendIds(String userId) {
        List<Friendship> friendships = friendshipRepository.findAllAcceptedFriends(userId);
        return friendships.stream()
                .map(f -> f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId())
                .collect(Collectors.toList());
    }

    /**
     * Kiểm tra user có ẩn trạng thái online không.
     * Dùng field {@code showOnlineStatus} trong {@link UserSetting}.
     */
    private boolean isStatusHidden(String userId) {
        return userSettingRepository.findById(userId)
                .map(s -> Boolean.FALSE.equals(s.getShowOnlineStatus()))
                .orElse(false);
    }
}
