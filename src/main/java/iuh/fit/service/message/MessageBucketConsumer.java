package iuh.fit.service.message;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import iuh.fit.configuration.KafkaConfig;
import iuh.fit.entity.Message;
import iuh.fit.service.search.SearchService;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.entity.UserDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Consumer — write-behind persistence of chat messages.
 *
 * <p>
 * Listens on {@code chat.messages} topic and writes each message into
 * the MongoDB bucket storage via {@link MessageBucketService}.
 *
 * <p>
 * On failure (e.g. optimistic lock conflict) it retries up to 3 times;
 * if still failing the message is forwarded to the DLQ topic.
 *
 * <p>
 * Ordering: messages are partitioned by conversationId so they arrive
 * in the same order they were produced within a conversation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBucketConsumer {

    private final MessageBucketService messageBucketService;
    private final SearchService searchService;
    private final UserDetailRepository userDetailRepository;
    private final KafkaTemplate<String, Message> messageKafkaTemplate;

    private static final int MAX_RETRIES = 3;

    @KafkaListener(topics = KafkaConfig.TOPIC_MESSAGES, groupId = "${spring.kafka.consumer.group-id:fruvia-message-group}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, Message> record) {
        Message message = record.value();
        if (message == null || message.getMessageId() == null) {
            log.warn("Received null or invalid message from Kafka, skipping");
            return;
        }

        log.debug("Kafka consumed message {} for conversation {}",
                message.getMessageId(), message.getConversationId());

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                // Idempotency guard — skip if already persisted
                if (messageBucketService.existsByMessageId(message.getMessageId())) {
                    log.debug("Message {} already persisted, skipping duplicate", message.getMessageId());
                    return;
                }
                // 1. Persist to MongoDB bucket (primary storage)
                int bucketSeq = messageBucketService.addMessageToBucket(message);

                // 2. Index in Elasticsearch for full-text search
                String senderName = userDetailRepository.findByUserId(message.getSenderId())
                        .map(UserDetail::getDisplayName)
                        .orElse("Unknown");
                searchService.indexMessage(message, senderName, bucketSeq);

                log.debug("Message {} persisted to bucket (seq={}) and indexed",
                        message.getMessageId(), bucketSeq);
                return; // success
            } catch (OptimisticLockingFailureException e) {
                retries++;
                log.warn("Optimistic lock conflict for message {} (attempt {}/{})",
                        message.getMessageId(), retries, MAX_RETRIES);
                if (retries >= MAX_RETRIES) {
                    sendToDlq(message, e);
                }
            } catch (Exception e) {
                log.error("Failed to process message {} from Kafka: {}",
                        message.getMessageId(), e.getMessage(), e);
                sendToDlq(message, e);
                return;
            }
        }
    }

    private void sendToDlq(Message message, Exception cause) {
        try {
            log.error("Sending message {} to DLQ after failure: {}",
                    message.getMessageId(), cause.getMessage());
            messageKafkaTemplate.send(
                    KafkaConfig.TOPIC_MESSAGES_DLQ,
                    message.getConversationId(),
                    message);
        } catch (Exception dlqEx) {
            log.error("Failed to send message {} to DLQ: {}",
                    message.getMessageId(), dlqEx.getMessage(), dlqEx);
        }
    }
}
