package iuh.fit.service.message;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import iuh.fit.configuration.KafkaConfig;
import iuh.fit.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer — publishes messages to the {@code chat.messages} topic.
 *
 * <p>
 * Key = conversationId (guarantees per-conversation ordering within a
 * Kafka partition). The Kafka consumer will read from this topic and
 * persist messages into MessageBuckets (write-behind pattern).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageProducerService {

    private final KafkaTemplate<String, Message> messageKafkaTemplate;

    /**
     * Send a message to Kafka asynchronously.
     *
     * @param message the message entity (already saved to Redis cache)
     */
    public void send(Message message) {
        String key = message.getConversationId();

        CompletableFuture<SendResult<String, Message>> future = messageKafkaTemplate.send(KafkaConfig.TOPIC_MESSAGES,
                key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka send failed for message {} in conversation {}: {}",
                        message.getMessageId(), key, ex.getMessage());
            } else {
                log.debug("Kafka message sent: {} -> partition {} offset {}",
                        message.getMessageId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
