package iuh.fit.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket Configuration for STOMP messaging.
 * Configures endpoints, message broker, heartbeat, and JWT authentication.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple broker với heartbeat: server ping mỗi 25s, expect client pong trong
        // 25s
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] { 25000, 25000 })
                .setTaskScheduler(heartbeatScheduler());

        // /app prefix cho @MessageMapping handlers
        config.setApplicationDestinationPrefixes("/app");

        // /user prefix cho user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    /**
     * TaskScheduler required by the simple broker when heartbeat is enabled.
     */
    private TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Standard SockJS endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Native WebSocket endpoint with logging interceptor
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.http.server.ServerHttpResponse response,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            java.util.Map<String, Object> attributes) throws Exception {
                        System.out.println(
                                "[WS-SERVER-DEBUG] Incoming handshake request from: " + request.getRemoteAddress());
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                });
    }

    /**
     * Đăng ký ChannelInterceptor để xác thực JWT trên frame CONNECT.
     * Interceptor sẽ set Principal (userId) vào session.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
