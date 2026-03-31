package iuh.fit.service.search;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import iuh.fit.entity.Message;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.elasticsearch.MessageSearchRepository;
import iuh.fit.repository.elasticsearch.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final MessageSearchRepository messageSearchRepository;
    private final UserSearchRepository userSearchRepository;
    private final MessageRepository messageRepository;
    private final UserDetailRepository userDetailRepository;
    private final UserAuthRepository userAuthRepository;

    // ── Message indexing ──────────────────────────────────────────────────────

    public void indexMessage(Message message, String senderName) {
        if (message.getContent() == null || message.getContent().isBlank()) return;

        MessageDocument doc = MessageDocument.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(senderName)
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt())
                .build();

        try {
            messageSearchRepository.save(doc);
        } catch (Exception e) {
            log.warn("Failed to index message {}: {}", message.getMessageId(), e.getMessage());
        }
    }

    public void deleteMessageIndex(String messageId) {
        try {
            messageSearchRepository.deleteById(messageId);
        } catch (Exception e) {
            log.warn("Failed to delete message index {}: {}", messageId, e.getMessage());
        }
    }

    // ── User indexing ─────────────────────────────────────────────────────────

    public void indexUser(UserDetail userDetail, String phoneNumber, String email) {
        UserDocument doc = UserDocument.builder()
                .userId(userDetail.getUserId())
                .displayName(userDetail.getDisplayName())
                .phoneNumber(phoneNumber)
                .email(email)
                .avatarUrl(userDetail.getAvatarUrl())
                .build();

        try {
            userSearchRepository.save(doc);
        } catch (Exception e) {
            log.warn("Failed to index user {}: {}", userDetail.getUserId(), e.getMessage());
        }
    }

    // ── Search messages ───────────────────────────────────────────────────────

    public Page<MessageDocument> searchMessages(String query, String conversationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return messageSearchRepository.searchByContentAndConversationId(query, conversationId, pageable);
    }

    // ── Search users ──────────────────────────────────────────────────────────

    public Page<UserDocument> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userSearchRepository.searchUsers(query, pageable);
    }

    // ── Bulk reindex (for initial data migration) ─────────────────────────────

    public void reindexAllMessages() {
        log.info("Starting bulk reindex of all messages...");
        List<Message> messages = messageRepository.findAll();
        int count = 0;
        for (Message msg : messages) {
            if (msg.getContent() == null || msg.getContent().isBlank()) continue;
            if (Boolean.TRUE.equals(msg.getIsDeleted()) || Boolean.TRUE.equals(msg.getIsRecalled())) continue;

            String senderName = userDetailRepository.findByUserId(msg.getSenderId())
                    .map(UserDetail::getDisplayName)
                    .orElse("Unknown");

            indexMessage(msg, senderName);
            count++;
        }
        log.info("Reindexed {} messages", count);
    }

    public void reindexAllUsers() {
        log.info("Starting bulk reindex of all users...");
        List<UserDetail> users = userDetailRepository.findAll();
        int count = 0;
        for (UserDetail user : users) {
            UserAuth auth = userAuthRepository.findById(user.getUserId()).orElse(null);
            String phone = auth != null ? auth.getPhoneNumber() : "";
            String email = auth != null ? auth.getEmail() : "";
            indexUser(user, phone, email);
            count++;
        }
        log.info("Reindexed {} users", count);
    }
}
