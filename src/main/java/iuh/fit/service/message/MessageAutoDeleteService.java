package iuh.fit.service.message;

import iuh.fit.entity.Conversations;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageAutoDeleteService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * Runs every 5 minutes to delete messages older than the configured auto-delete
     * duration.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void deleteExpiredMessages() {
        List<Conversations> conversations = conversationRepository.findByAutoDeleteDurationNotNull();

        for (Conversations conv : conversations) {
            String duration = conv.getAutoDeleteDuration();
            if (duration == null || duration.isEmpty())
                continue;

            int days;
            switch (duration) {
                case "1d":
                    days = 1;
                    break;
                case "7d":
                    days = 7;
                    break;
                case "30d":
                    days = 30;
                    break;
                default:
                    continue; // skip invalid durations
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
            long deleted = messageRepository.deleteByConversationIdAndCreatedAtBefore(
                    conv.getConversationId(), cutoff);

            if (deleted > 0) {
                log.info("Auto-deleted {} messages from conversation {} (duration: {})",
                        deleted, conv.getConversationId(), duration);
            }
        }
    }
}
