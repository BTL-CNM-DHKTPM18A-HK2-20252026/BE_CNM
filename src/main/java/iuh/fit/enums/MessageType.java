package iuh.fit.enums;

/**
 * Enum for message types
 */
public enum MessageType {
    TEXT, // Text message
    IMAGE, // Image message
    VIDEO, // Video message
    MEDIA, // Other media types
    VOICE, // Voice message
    LINK, // Shared Link message
    SYSTEM, // System notification message (e.g. role changes)
    SHARE_CONTACT, // Shared contact card message
    CALL_MISSED, // Call history: missed call (caller cancelled before answer)
    CALL_REJECTED, // Call history: callee rejected the call
    CALL_ENDED // Call history: call ended normally (content = duration in seconds)
}
