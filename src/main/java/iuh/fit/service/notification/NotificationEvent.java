package iuh.fit.service.notification;

import java.util.Map;

import org.springframework.context.ApplicationEvent;

import iuh.fit.enums.NotificationType;
import lombok.Getter;

/**
 * Spring ApplicationEvent đại diện cho 1 sự kiện sinh notification.
 * Pattern: Actor → Verb (type) → Object → Target.
 *
 * Publish bằng ApplicationEventPublisher.publishEvent(...) — sẽ được
 * NotificationService xử lý ASYNC (persist + push qua Redis Pub/Sub).
 */
@Getter
public class NotificationEvent extends ApplicationEvent {

    private final String receiverId; // Người nhận
    private final String actorId; // Người gây ra (null cho SYSTEM)
    private final NotificationType type;
    private final String objectType; // POST / COMMENT / MESSAGE / FRIENDSHIP / STORY
    private final String objectId;
    private final String targetType; // optional
    private final String targetId; // optional
    private final String deepLink; // optional
    private final Map<String, Object> metadata;

    public NotificationEvent(Object source,
            String receiverId,
            String actorId,
            NotificationType type,
            String objectType,
            String objectId,
            String targetType,
            String targetId,
            String deepLink,
            Map<String, Object> metadata) {
        super(source);
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.objectType = objectType;
        this.objectId = objectId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.deepLink = deepLink;
        this.metadata = metadata;
    }

    // ── Helpers builder ────────────────────────────────────────────────────

    public static NotificationEvent forFriendRequest(Object src, String receiverId, String actorId,
            String friendshipId) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.FRIEND_REQUEST,
                "FRIENDSHIP", friendshipId, null, null,
                "/friends/requests", null);
    }

    public static NotificationEvent forFriendAccepted(Object src, String receiverId, String actorId,
            String friendshipId) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.FRIEND_REQUEST_ACCEPTED,
                "FRIENDSHIP", friendshipId, null, null,
                "/profile/" + actorId, null);
    }

    public static NotificationEvent forPostReaction(Object src, String receiverId, String actorId, String postId,
            String reactionType) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.POST_REACTION,
                "POST", postId, null, null,
                "/posts/" + postId,
                Map.of("reactionType", reactionType == null ? "LIKE" : reactionType));
    }

    public static NotificationEvent forPostComment(Object src, String receiverId, String actorId,
            String postId, String commentId, String snippet) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.POST_COMMENT,
                "COMMENT", commentId, "POST", postId,
                "/posts/" + postId + "?comment=" + commentId,
                snippet == null ? null : Map.of("snippet", snippet));
    }

    public static NotificationEvent forCommentReply(Object src, String receiverId, String actorId,
            String postId, String commentId, String snippet) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.POST_COMMENT_REPLY,
                "COMMENT", commentId, "POST", postId,
                "/posts/" + postId + "?comment=" + commentId,
                snippet == null ? null : Map.of("snippet", snippet));
    }

    public static NotificationEvent forCommentReaction(Object src, String receiverId, String actorId,
            String postId, String commentId, String reactionType) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.COMMENT_REACTION,
                "COMMENT", commentId, "POST", postId,
                "/posts/" + postId + "?comment=" + commentId,
                Map.of("reactionType", reactionType == null ? "LIKE" : reactionType));
    }

    public static NotificationEvent forNewMessage(Object src, String receiverId, String actorId,
            String conversationId, String messageId, String preview) {
        return new NotificationEvent(src, receiverId, actorId, NotificationType.MESSAGE_NEW,
                "MESSAGE", messageId, "CONVERSATION", conversationId,
                "/chat/" + conversationId,
                preview == null ? null : Map.of("preview", preview));
    }
}
