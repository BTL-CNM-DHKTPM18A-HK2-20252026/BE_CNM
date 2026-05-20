package iuh.fit.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import iuh.fit.service.notification.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Pub/Sub cho Notification System.
 * Tách container riêng để không động chạm container session-kick / presence.
 */
@Configuration
@Slf4j
public class NotificationRedisConfig {

    @Bean(name = "notificationListenerContainer")
    public RedisMessageListenerContainer notificationListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationDispatcher dispatcher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(5000L);
        container.setErrorHandler(err -> log.warn("[Notif Redis] Listener runtime error: {}", err.getMessage()));

        try (RedisConnection conn = connectionFactory.getConnection()) {
            conn.ping();
            container.addMessageListener(dispatcher, new PatternTopic("notif:*"));
            log.info("[Notif Redis] Subscribed to pattern 'notif:*'");
        } catch (Exception e) {
            log.warn("[Notif Redis] Redis unavailable at startup, notif listeners disabled. Will not auto-recover: {}",
                    e.getMessage());
        }

        return container;
    }
}
