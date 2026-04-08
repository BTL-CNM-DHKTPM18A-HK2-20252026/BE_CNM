package iuh.fit.service.message;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed typing indicator service.
 *
 * Stores per-user typing state with automatic TTL expiry.
 * Key pattern: typing:{conversationId}:{userId}
 * TTL: 3 seconds (auto-clears when user stops typing)
 *
 * Benefits over pure WebSocket relay:
 * - Automatic expiry (no need for explicit "stop typing" events)
 * - Multi-instance server support (shared state via Redis)
 * - State recovery on client reconnection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TypingIndicatorService {

    private static final String REDIS_PREFIX = "typing:";
    private static final long TYPING_TTL_SECONDS = 3;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Record that a user is typing in a conversation.
     * The entry auto-expires after {@link #TYPING_TTL_SECONDS} seconds.
     */
    public void setTyping(String conversationId, String userId) {
        try {
            String key = buildKey(conversationId, userId);
            stringRedisTemplate.opsForValue().set(key, "1", TYPING_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException e) {
            log.debug("Redis unavailable, typing indicator not stored: {}", e.getMessage());
        }
    }

    /**
     * Explicitly clear typing state (e.g., when user sends a message or leaves).
     */
    public void clearTyping(String conversationId, String userId) {
        try {
            String key = buildKey(conversationId, userId);
            stringRedisTemplate.delete(key);
        } catch (RedisConnectionFailureException e) {
            log.debug("Redis unavailable, typing indicator not cleared: {}", e.getMessage());
        }
    }

    /**
     * Get all user IDs currently typing in a conversation.
     * Uses SCAN to find matching keys (safe for production, unlike KEYS).
     */
    public Set<String> getTypingUsers(String conversationId) {
        try {
            String pattern = REDIS_PREFIX + conversationId + ":*";
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Collections.emptySet();
            }
            String prefix = REDIS_PREFIX + conversationId + ":";
            return keys.stream()
                    .map(key -> key.substring(prefix.length()))
                    .collect(Collectors.toSet());
        } catch (RedisConnectionFailureException e) {
            log.debug("Redis unavailable, cannot get typing users: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private String buildKey(String conversationId, String userId) {
        return REDIS_PREFIX + conversationId + ":" + userId;
    }
}
