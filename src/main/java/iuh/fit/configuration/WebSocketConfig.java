package iuh.fit.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for STOMP messaging.
 * Configures endpoints and message broker for real-time communication.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to carry the greetings back to the client
        // on destinations prefixed with /topic
        config.enableSimpleBroker("/topic", "/queue");
        
        // Designate the /app prefix for messages that are bound for methods annotated with @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
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
                        System.out.println("[WS-SERVER-DEBUG] Incoming handshake request from: " + request.getRemoteAddress());
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                });
    }
}
