package iuh.fit.configuration;

import java.security.Principal;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocketAuthInterceptor — xác thực JWT trên frame CONNECT của STOMP.
 *
 * <p>
 * Khi client gửi frame CONNECT, header {@code Authorization: Bearer <token>}
 * được lấy ra, verify chữ ký HS512, kiểm tra expiration, rồi set
 * {@link Principal} = userId (sub claim) vào accessor.
 *
 * <p>
 * Nếu token invalid → throw {@link IllegalArgumentException} → Spring
 * từ chối kết nối.
 *
 * <h3>Security flow:</h3>
 * 
 * <pre>
 *  Client CONNECT frame
 *     └─ Header: Authorization: Bearer &lt;jwt&gt;
 *         └─ Interceptor: verify → extract sub → set Principal
 *             └─ PresenceEventListener receives SessionConnectEvent
 *                 └─ PresenceService.userConnected(userId)
 * </pre>
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.access-token.secret}")
    private String accessTokenSecret;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String userId = validateTokenAndGetUserId(token);
                    // Set Principal để PresenceEventListener & SimpUserRegistry có thể dùng
                    accessor.setUser(new StompPrincipal(userId));
                    log.info("[WS-Auth] Authenticated userId={} on CONNECT", userId);
                } catch (Exception e) {
                    log.error("[WS-Auth] JWT validation failed: {}", e.getMessage());
                    throw new IllegalArgumentException("Invalid or expired JWT token");
                }
            } else {
                log.warn("[WS-Auth] CONNECT frame missing Authorization header");
                // Cho phép anonymous connect nếu cần (ví dụ QR login flow)
                // Nếu muốn bắt buộc auth thì throw exception ở đây
            }
        }
        return message;
    }

    /**
     * Verify JWT bằng HS512 (Nimbus JOSE) — cùng thuật toán với SecurityConfig.
     *
     * @return userId (sub claim)
     */
    private String validateTokenAndGetUserId(String token) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(token);
        SecretKey secretKey = new SecretKeySpec(accessTokenSecret.getBytes(), "HmacSHA512");
        JWSVerifier verifier = new MACVerifier(secretKey);

        if (!signedJWT.verify(verifier)) {
            throw new SecurityException("JWT signature verification failed");
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        if (claims.getExpirationTime() != null
                && claims.getExpirationTime().before(new java.util.Date())) {
            throw new SecurityException("JWT token has expired");
        }

        String tokenType = claims.getStringClaim("type");
        if (!"access".equals(tokenType)) {
            throw new SecurityException("JWT token type is not access");
        }

        return claims.getSubject(); // sub = userId
    }

    /**
     * Simple Principal implementation wrapping userId.
     */
    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
