package iuh.fit.service.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import iuh.fit.dto.response.notification.NotificationResponse;
import iuh.fit.entity.Notification;
import iuh.fit.entity.UserDetail;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.ForbiddenException;
import iuh.fit.mapper.NotificationMapper;
import iuh.fit.repository.NotificationRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NotificationService — xử lý NotificationEvent ASYNC + cung cấp REST API ops.
 * Theo NOTIFICATION_IMPLEMENTATION_PLAN.md.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final long AGGREGATE_WINDOW_HOURS = 24;

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationBuilder notificationBuilder;
    private final UserDetailRepository userDetailRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════════════════════════════════════
    // EVENT HANDLER (ASYNC) — entry point từ ApplicationEventPublisher
    // ════════════════════════════════════════════════════════════════════════

    @Async
    @EventListener
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            if (event.getReceiverId() == null)
                return;
            // Skip self-notification (actor == receiver)
            if (event.getActorId() != null && event.getActorId().equals(event.getReceiverId()))
                return;

            String groupKey = notificationBuilder.renderGroupKey(event);

            // ── Snapshot actor info ─────────────────────────────────────────
            String actorName = null;
            String actorAvatar = null;
            if (event.getActorId() != null) {
                Optional<UserDetail> ud = userDetailRepository.findByUserId(event.getActorId());
                if (ud.isPresent()) {
                    actorName = ud.get().getDisplayName();
                    actorAvatar = ud.get().getAvatarUrl();
                }
            }

            // ── Aggregation: gom nếu cùng groupKey trong 24h ────────────────
            LocalDateTime since = LocalDateTime.now().minusHours(AGGREGATE_WINDOW_HOURS);
            Optional<Notification> existing = notificationRepository
                    .findFirstByGroupKeyAndReceiverIdAndCreatedAtAfterOrderByCreatedAtDesc(
                            groupKey, event.getReceiverId(), since);

            Notification saved;
            if (existing.isPresent() && shouldAggregate(event)) {
                Notification n = existing.get();
                int newCount = (n.getAggregateCount() == null ? 1 : n.getAggregateCount()) + 1;
                n.setAggregateCount(newCount);
                n.setActorId(event.getActorId()); // actor mới nhất
                n.setActorName(actorName);
                n.setActorAvatarUrl(actorAvatar);
                n.setBody(notificationBuilder.renderBodyForAggregated(n, actorName));
                n.setIsRead(false); // reset unread khi có hoạt động mới
                n.setUpdatedAt(LocalDateTime.now());
                saved = notificationRepository.save(n);
            } else {
                Notification n = Notification.builder()
                        .receiverId(event.getReceiverId())
                        .actorId(event.getActorId())
                        .actorName(actorName)
                        .actorAvatarUrl(actorAvatar)
                        .notificationType(event.getType())
                        .objectType(event.getObjectType())
                        .objectId(event.getObjectId())
                        .entityId(event.getObjectId())
                        .targetType(event.getTargetType())
                        .targetId(event.getTargetId())
                        .deepLink(event.getDeepLink())
                        .body(notificationBuilder.renderBody(event, actorName))
                        .groupKey(groupKey)
                        .aggregateCount(1)
                        .metadata(event.getMetadata())
                        .isRead(false)
                        .isDeleted(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                saved = notificationRepository.save(n);
            }

            // ── Push qua Redis Pub/Sub channel notif:{userId} ───────────────
            publishToRedis(saved);

        } catch (Exception e) {
            log.warn("[Notif] handleNotificationEvent failed: {}", e.getMessage(), e);
        }
    }

    private boolean shouldAggregate(NotificationEvent event) {
        // Friend request không gom (mỗi request là 1 hành động độc lập)
        return switch (event.getType()) {
            case FRIEND_REQUEST, FRIEND_REQUEST_ACCEPTED, MESSAGE_NEW, SYSTEM -> false;
            default -> true;
        };
    }

    private void publishToRedis(Notification saved) {
        try {
            NotificationResponse payload = notificationMapper.toResponse(saved);
            String json = objectMapper.writeValueAsString(payload);
            String channel = NotificationDispatcher.CHANNEL_PREFIX + saved.getReceiverId();
            stringRedisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.warn("[Notif] Redis publish failed: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // REST API operations
    // ════════════════════════════════════════════════════════════════════════

    public Page<NotificationResponse> getUserNotifications(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository
                .findByReceiverIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    /**
     * Cursor-based pagination: lấy notification cũ hơn cursor (timestamp).
     */
    public List<NotificationResponse> getUserNotificationsCursor(String userId, LocalDateTime cursor, int size) {
        LocalDateTime before = cursor == null ? LocalDateTime.now().plusDays(1) : cursor;
        return notificationRepository
                .findByReceiverIdAndIsDeletedFalseAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, before, PageRequest.of(0, size))
                .stream()
                .map(notificationMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getUnreadNotifications(String userId) {
        return notificationRepository.findByReceiverIdAndIsReadFalseAndIsDeletedFalse(userId)
                .stream()
                .map(notificationMapper::toResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByReceiverIdAndIsReadFalseAndIsDeletedFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(String notificationId, String userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!n.getReceiverId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        n.setIsRead(true);
        n.setUpdatedAt(LocalDateTime.now());
        return notificationMapper.toResponse(notificationRepository.save(n));
    }

    @Transactional
    public int markAllAsRead(String userId) {
        List<Notification> unread = notificationRepository
                .findByReceiverIdAndIsReadFalseAndIsDeletedFalse(userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> {
            n.setIsRead(true);
            n.setUpdatedAt(now);
        });
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void deleteNotification(String notificationId, String userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!n.getReceiverId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        n.setIsDeleted(true);
        n.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    /**
     * Cập nhật action status cho notification dạng FRIEND_REQUEST
     * (PENDING / ACCEPTED / REJECTED) — phục vụ optimistic UI bên FE.
     */
    @Transactional
    public NotificationResponse updateActionStatus(String notificationId, String userId, String status) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!n.getReceiverId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        n.setActionStatus(status);
        n.setIsRead(true);
        n.setUpdatedAt(LocalDateTime.now());
        return notificationMapper.toResponse(notificationRepository.save(n));
    }
}
