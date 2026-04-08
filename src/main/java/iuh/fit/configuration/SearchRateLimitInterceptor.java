package iuh.fit.configuration;

import java.io.IOException;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import iuh.fit.response.ApiResponse;
import iuh.fit.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String RATE_LIMIT_PREFIX = "rate_limit:search:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return true; // Let security handle unauthenticated requests
        }

        String key = RATE_LIMIT_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW);
        }

        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for user {} — {} requests/min", userId, count);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.builder()
                            .success(false)
                            .message("Rate limit exceeded. Max " + MAX_REQUESTS_PER_MINUTE
                                    + " search requests per minute.")
                            .build()));
            return false;
        }

        return true;
    }
}
