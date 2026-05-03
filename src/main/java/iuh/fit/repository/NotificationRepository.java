package iuh.fit.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Notification;
import iuh.fit.enums.NotificationType;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    // ── Feed query ─────────────────────────────────────────────────────────
    Page<Notification> findByReceiverIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String receiverId, Pageable pageable);

    // Cursor pagination: lấy notification cũ hơn timestamp
    List<Notification> findByReceiverIdAndIsDeletedFalseAndCreatedAtLessThanOrderByCreatedAtDesc(
            String receiverId, LocalDateTime before, Pageable pageable);

    // ── Unread ─────────────────────────────────────────────────────────────
    List<Notification> findByReceiverIdAndIsReadFalseAndIsDeletedFalse(String receiverId);

    long countByReceiverIdAndIsReadFalseAndIsDeletedFalse(String receiverId);

    // ── By type ────────────────────────────────────────────────────────────
    List<Notification> findByReceiverIdAndNotificationTypeAndIsDeletedFalse(
            String receiverId, NotificationType type);

    // ── Aggregation lookup ────────────────────────────────────────────────
    // Tìm notification cùng groupKey trong 24h gần nhất để gom nhóm
    Optional<Notification> findFirstByGroupKeyAndReceiverIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String groupKey, String receiverId, LocalDateTime since);
}
