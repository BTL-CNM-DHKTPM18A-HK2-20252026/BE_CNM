package iuh.fit.enums;

/**
 * Enum for message types
 */
public enum MessageType {
    TEXT, // Text message
    IMAGE, // Image message
    IMAGE_GROUP, // Multiple images in one message (album/grid)
    VIDEO, // Video message
    MEDIA, // Other media types
    VOICE, // Voice message
    LINK, // Shared Link message
    SYSTEM, // System notification message (e.g. role changes)
    SHARE_CONTACT, // Shared contact card message
    CALL_MISSED, // Call history: missed call (caller cancelled before answer)
    CALL_REJECTED, // Call history: callee rejected the call
    CALL_ENDED, // Call history: call ended normally (content = duration in seconds)
    CALL_GROUP_START, // Group call started
    POLL, // Group poll message
    STORY_REPLY // Reply to a story
}
