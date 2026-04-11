package iuh.fit.service.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import iuh.fit.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Layer 3 — Sliding Message Cache ("The Speed Booster")
 *
 * <p>
 * Cache 50 tin nhắn mới nhất mỗi conversation trên Redis ZSET.
 * Tải tin nhắn trong &lt;5ms thay vì query MongoDB.
 *
 * <h3>Redis Structure:</h3>
 * 
 * <pre>
 * Key   : chat:messages:{conversationId}
 * Type  : Sorted Set (ZSET)
 * Score : createdAt epoch millis
 * Value : Message (JSON via GenericJackson2JsonRedisSerializer)
 * </pre>
 *
 * <h3>Sliding Expiration:</h3>
 * <ul>
 * <li>Mỗi lần Đọc/Ghi → EXPIRE reset về 30 phút</li>
 * <li>Phòng chat không hoạt động 30 phút → Redis tự dọn → tiết kiệm RAM</li>
 * <li>Cache miss → fallback DB → warm-up lại nếu cần</li>
 * </ul>
 *
 * <h3>Error Handling:</h3>
 * Nếu Redis down → trả kết quả rỗng → caller fallback sang MongoDB.
 * Không bao giờ throw exception ra ngoài — chat vẫn hoạt động bình thường.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** Maximum number of recent messages cached per conversation. */
    public static final int MAX_CACHED = 50;

    /** Sliding TTL: 60 phút — tối ưu RAM cho 100k users, reset mỗi lần đọc/ghi. */
    private static final long CACHE_TTL_MINUTES = 60;

    private static final String KEY_PREFIX = "chat:messages:";

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Push a single message into the conversation's cache.
     * Uses epoch-millis of {@code createdAt} as score for time ordering.
     * Resets sliding TTL to 30 minutes on every write.
     */
    public void pushMessage(Message message) {
        try {
            String key = keyOf(message.getConversationId());
            double score = toScore(message);

            redisTemplate.opsForZSet().add(key, message, score);

            // Trim: keep only the newest MAX_CACHED entries
            long size = size(key);
            if (size > MAX_CACHED) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_CACHED - 1);
            }

            // Sliding expiration: reset TTL on write
            slidingExpire(key);
        } catch (RedisConnectionFailureException ex) {
            log.warn("[MsgCache] Redis unavailable, message not cached: {}", ex.getMessage());
        }
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Get the newest {@code count} messages for a conversation (DESC order).
     * Resets sliding TTL on read — active conversations stay warm.
     *
     * @return messages newest-first, or empty list on cache miss / Redis error
     */
    public List<Message> getRecentMessages(String conversationId, int count) {
        try {
            String key = keyOf(conversationId);
            var raw = redisTemplate.opsForZSet().reverseRange(key, 0, count - 1);
            if (raw == null || raw.isEmpty())
                return Collections.emptyList();

            // Sliding expiration: reset TTL on read
            slidingExpire(key);

            List<Message> result = new ArrayList<>(raw.size());
            for (Object obj : raw) {
                if (obj instanceof Message m) {
                    result.add(m);
                }
            }
            return result;
        } catch (RedisConnectionFailureException ex) {
            log.warn("[MsgCache] Redis unavailable, returning empty: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get messages whose score (epoch ms) is strictly less than {@code beforeMs}.
     * Used for scroll-up pagination within the cache window.
     * Resets sliding TTL on read.
     */
    public List<Message> getMessagesBefore(String conversationId, long beforeMs, int count) {
        try {
            String key = keyOf(conversationId);
            var raw = redisTemplate.opsForZSet()
                    .reverseRangeByScore(key, 0, beforeMs - 1, 0, count);
            if (raw == null || raw.isEmpty())
                return Collections.emptyList();

            // Sliding expiration: reset TTL on read
            slidingExpire(key);

            List<Message> result = new ArrayList<>(raw.size());
            for (Object obj : raw) {
                if (obj instanceof Message m) {
                    result.add(m);
                }
            }
            return result;
        } catch (RedisConnectionFailureException ex) {
            log.warn("[MsgCache] Redis unavailable, returning empty: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Check whether the cache has any data for this conversation.
     */
    public boolean hasCachedMessages(String conversationId) {
        try {
            return size(keyOf(conversationId)) > 0;
        } catch (RedisConnectionFailureException ex) {
            return false;
        }
    }

    /**
     * Return the epoch-ms score of the oldest cached message (lowest score).
     * If the cache is empty returns {@link Long#MAX_VALUE}.
     */
    public long oldestCachedTimestamp(String conversationId) {
        try {
            String key = keyOf(conversationId);
            var oldest = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
            if (oldest == null || oldest.isEmpty())
                return Long.MAX_VALUE;
            Double score = oldest.iterator().next().getScore();
            return score != null ? score.longValue() : Long.MAX_VALUE;
        } catch (RedisConnectionFailureException ex) {
            return Long.MAX_VALUE;
        }
    }

    // ── Eviction ─────────────────────────────────────────────────────────────

    /**
     * Invalidate (delete) the entire cache for a conversation.
     * Called when messages are bulk-deleted (e.g. clearConversationAll).
     */
    public void evict(String conversationId) {
        try {
            redisTemplate.delete(keyOf(conversationId));
        } catch (RedisConnectionFailureException ex) {
            log.warn("[MsgCache] Redis unavailable, eviction skipped: {}", ex.getMessage());
        }
    }

    // ── Warm-up ──────────────────────────────────────────────────────────────

    /**
     * Populate the cache from a list of messages (e.g. fetched from DB).
     * Resets sliding TTL after warm-up.
     */
    public void warmUp(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty())
            return;
        try {
            String key = keyOf(conversationId);
            redisTemplate.delete(key);
            for (Message m : messages) {
                redisTemplate.opsForZSet().add(key, m, toScore(m));
            }
            long excess = size(key) - MAX_CACHED;
            if (excess > 0) {
                redisTemplate.opsForZSet().removeRange(key, 0, excess - 1);
            }
            slidingExpire(key);
        } catch (RedisConnectionFailureException ex) {
            log.warn("[MsgCache] Redis unavailable, warm-up skipped: {}", ex.getMessage());
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Sliding expiration: reset TTL mỗi lần đọc/ghi.
     * 30 phút không hoạt động → Redis tự dọn → tiết kiệm RAM.
     */
    private void slidingExpire(String key) {
        redisTemplate.expire(key, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private String keyOf(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    private long size(String key) {
        Long s = redisTemplate.opsForZSet().zCard(key);
        return s != null ? s : 0;
    }

    private double toScore(Message message) {
        if (message.getCreatedAt() == null)
            return System.currentTimeMillis();
        return message.getCreatedAt()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
