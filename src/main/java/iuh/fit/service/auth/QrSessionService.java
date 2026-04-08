package iuh.fit.service.auth;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for managing QR login sessions in Redis.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class QrSessionService {

    StringRedisTemplate redisTemplate;

    private static final String QR_PREFIX = "qr_session:";
    private static final long QR_TTL_SECONDS = 300; // 120 seconds as requested

    /**
     * Create a new QR Session UUID and store it in Redis with PENDING status.
     *
     * @return Generated UUID string
     */
    public String createQrSession() {
        String uuid = UUID.randomUUID().toString();
        String key = QR_PREFIX + uuid;

        log.info("Creating QR session: {} with {}s TTL", uuid, QR_TTL_SECONDS);

        // Save status PENDING to Redis
        redisTemplate.opsForValue().set(key, "PENDING", Duration.ofSeconds(QR_TTL_SECONDS));

        return uuid;
    }
}
