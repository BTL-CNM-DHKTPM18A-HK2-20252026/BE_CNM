package iuh.fit.service.notification;

import org.springframework.stereotype.Component;

import iuh.fit.entity.Notification;
import iuh.fit.enums.NotificationType;

/**
 * Render body text cho notification (i18n đơn giản, default Tiếng Việt).
 * Có thể mở rộng theo locale receiver sau.
 */
@Component
public class NotificationBuilder {

    public String renderBody(NotificationEvent event, String actorName) {
        String name = actorName == null || actorName.isBlank() ? "Một người dùng" : actorName;
        NotificationType t = event.getType();
        switch (t) {
            case FRIEND_REQUEST:
                return name + " đã gửi cho bạn lời mời kết bạn";
            case FRIEND_REQUEST_ACCEPTED:
                return name + " đã chấp nhận lời mời kết bạn của bạn";
            case FOLLOW:
                return name + " đã theo dõi bạn";
            case POST_REACTION:
                return name + " đã thích bài viết của bạn";
            case POST_COMMENT:
                return name + " đã bình luận về bài viết của bạn";
            case POST_COMMENT_REPLY:
                return name + " đã trả lời bình luận của bạn";
            case COMMENT_REACTION:
                return name + " đã thích bình luận của bạn";
            case POST_MENTION:
                return name + " đã nhắc đến bạn trong một bài viết";
            case POST_SHARE:
                return name + " đã chia sẻ bài viết của bạn";
            case MESSAGE_NEW:
                return name + " đã gửi tin nhắn cho bạn";
            case MESSAGE_REACTION:
                return name + " đã bày tỏ cảm xúc với tin nhắn của bạn";
            case MESSAGE_MENTION:
                return name + " đã nhắc đến bạn trong cuộc trò chuyện";
            case STORY_VIEW:
                return name + " đã xem story của bạn";
            case STORY_REACTION:
                return name + " đã bày tỏ cảm xúc với story của bạn";
            case SYSTEM:
                return "Bạn có thông báo mới từ hệ thống";
            default:
                return name + " đã có một hoạt động mới";
        }
    }

    /**
     * Group key dùng để aggregation 24h: gom các noti cùng type + cùng object.
     */
    public String renderGroupKey(NotificationEvent event) {
        return event.getType().name() + ":" +
                (event.getObjectType() == null ? "-" : event.getObjectType()) + ":" +
                (event.getObjectId() == null ? "-" : event.getObjectId());
    }

    public String renderBodyForAggregated(Notification existing, String newActorName) {
        int count = existing.getAggregateCount() == null ? 1 : existing.getAggregateCount();
        // count đã +1 mới khi gọi hàm này
        NotificationType t = existing.getNotificationType();
        String others = (count - 1) + " người khác";
        String name = newActorName == null ? "Một người dùng" : newActorName;
        switch (t) {
            case POST_REACTION:
                return name + " và " + others + " đã thích bài viết của bạn";
            case POST_COMMENT:
                return name + " và " + others + " đã bình luận về bài viết của bạn";
            case COMMENT_REACTION:
                return name + " và " + others + " đã thích bình luận của bạn";
            case STORY_VIEW:
                return name + " và " + others + " đã xem story của bạn";
            default:
                return name + " và " + others + " có hoạt động mới";
        }
    }
}
