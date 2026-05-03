package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.NotificationType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Notification entity — Activity Stream pattern (Actor → Verb → Object →
 * Target).
 * Theo NOTIFICATION_IMPLEMENTATION_PLAN.md.
 *
 * Indexes:
 * - (receiverId, createdAt desc) - feed query
 * - (receiverId, isRead, isDeleted) - unread count
 * - createdAt TTL 90 days - auto cleanup
 * - groupKey - aggregation 24h
 */
@Document(collection = "notification")
@CompoundIndexes({
        @CompoundIndex(name = "receiver_created_idx", def = "{'receiverId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "receiver_read_idx", def = "{'receiverId': 1, 'isRead': 1, 'isDeleted': 1}"),
        @CompoundIndex(name = "group_key_idx", def = "{'groupKey': 1, 'createdAt': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @Builder.Default
    String notificationId = UUID.randomUUID().toString();

    // ── Activity Stream core ───────────────────────────────────────────────
    String receiverId; // Người nhận
    String actorId; // Người gây ra hành động (có thể null cho SYSTEM)
    String actorName; // Snapshot tên (denormalized để render nhanh)
    String actorAvatarUrl; // Snapshot avatar
    NotificationType notificationType;

    // Object: entity bị tác động (post, comment, message, friendship...)
    String objectType; // "POST", "COMMENT", "MESSAGE", "FRIENDSHIP", "STORY"
    String objectId;
    String entityId; // Alias cho objectId (backward-compat)

    // Target: ngữ cảnh phụ (conversation chứa message, post chứa comment)
    String targetType;
    String targetId;

    // ── Hiển thị ──────────────────────────────────────────────────────────
    String title; // Optional - chủ yếu dùng body
    String body; // Pre-rendered text (i18n key resolved hoặc plain)
    String iconUrl; // Optional override
    String deepLink; // VD: /posts/{id}, /chat/{conv}, /profile/{user}

    // ── Aggregation ───────────────────────────────────────────────────────
    String groupKey; // {type}:{objectType}:{objectId} — gom nhóm 24h
    Integer aggregateCount; // số actor cộng dồn (mặc định 1)

    // ── State ─────────────────────────────────────────────────────────────
    @Builder.Default
    Boolean isRead = false;
    @Builder.Default
    Boolean isDeleted = false;
    String actionStatus; // PENDING / ACCEPTED / REJECTED / null - cho FRIEND_REQUEST

    // ── Metadata bổ sung ──────────────────────────────────────────────────
    Map<String, Object> metadata; // VD: reactionType, commentSnippet...

    // ── TTL (90 days) ─────────────────────────────────────────────────────
    @Indexed(name = "createdAt_ttl_idx", expireAfterSeconds = 60 * 60 * 24 * 90)
    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
