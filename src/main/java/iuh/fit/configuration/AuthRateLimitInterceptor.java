package iuh.fit.configuration;

import java.io.IOException;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import iuh.fit.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate limiter cho các auth endpoints nhạy cảm (check-email, check-phone,
 * login).
 * Giới hạn 10 requests/phút theo IP để ngăn user enumeration và brute-force.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String RATE_LIMIT_PREFIX = "rate_limit:auth:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String ip = resolveClientIp(request);
        String key = RATE_LIMIT_PREFIX + ip;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW);
        }

        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Auth rate limit exceeded for IP {} — {} requests/min", ip, count);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.builder()
                            .success(false)
                            .message("Too many requests. Please try again later.")
                            .build()));
            return false;
        }

        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
