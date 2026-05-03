package iuh.fit.enums;

/**
 * Enum cho các loại notification trong hệ thống Fruvia.
 * Theo NOTIFICATION_IMPLEMENTATION_PLAN.md - Activity Stream taxonomy.
 */
public enum NotificationType {
    // Social - Friend
    FRIEND_REQUEST, // Gửi lời mời kết bạn
    FRIEND_REQUEST_ACCEPTED, // Đã chấp nhận lời mời

    // Social - Follow (nếu hệ thống có)
    FOLLOW, // Người khác theo dõi bạn

    // Post / Content interactions
    POST_REACTION, // Like/react bài viết của bạn
    POST_COMMENT, // Bình luận bài viết của bạn
    POST_COMMENT_REPLY, // Trả lời bình luận của bạn
    COMMENT_REACTION, // Like/react bình luận của bạn
    POST_MENTION, // @mention bạn trong bài viết / bình luận
    POST_SHARE, // Bài viết của bạn được share

    // Messaging
    MESSAGE_NEW, // Tin nhắn mới (1-1 / nhóm)
    MESSAGE_REACTION, // React tin nhắn của bạn
    MESSAGE_MENTION, // @mention bạn trong nhóm

    // Story
    STORY_VIEW, // Bạn bè xem story của bạn
    STORY_REACTION, // React story

    // System
    SYSTEM, // Thông báo hệ thống

    // Legacy (backward-compat) - giữ để không break dữ liệu cũ
    FRIEND_REQ,
    LIKE_POST
}
