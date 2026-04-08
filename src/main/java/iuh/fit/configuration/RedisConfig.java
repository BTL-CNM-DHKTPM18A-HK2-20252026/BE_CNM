package iuh.fit.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import iuh.fit.service.presence.SessionKickSubscriber;

/**
 * Redis Configuration — 3-Layer Architecture
 *
 * <ul>
 *   <li>Layer 1 (Session): StringRedisTemplate → Hash user:session:{userId}</li>
 *   <li>Layer 2 (Presence): StringRedisTemplate → String user:presence:{userId}</li>
 *   <li>Layer 3 (Message Cache): RedisTemplate&lt;String, Object&gt; → ZSET chat:messages:{convId}</li>
 *   <li>Pub/Sub: session:kick channel for Zalo-style single-tab enforcement</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate — dùng cho Layer 1 (Session) và Layer 2 (Presence).
     * Hiệu suất cao nhất vì serialize/deserialize thuần String.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * RedisTemplate&lt;String, Object&gt; — dùng cho Layer 3 (Message Cache).
     * Cần GenericJackson2JsonRedisSerializer để serialize Message entity.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    // ── Redis Pub/Sub for session kick-out (Zalo-style single tab) ──────────

    public static final String SESSION_KICK_CHANNEL = "session:kick";

    @Bean
    public ChannelTopic sessionKickTopic() {
        return new ChannelTopic(SESSION_KICK_CHANNEL);
    }

    @Bean
    public MessageListenerAdapter sessionKickListenerAdapter(SessionKickSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter sessionKickListenerAdapter,
            ChannelTopic sessionKickTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(sessionKickListenerAdapter, sessionKickTopic);
        return container;
    }
}
