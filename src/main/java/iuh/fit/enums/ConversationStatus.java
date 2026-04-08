package iuh.fit.enums;

/**
 * Status of a conversation — controls inbox placement and UI behavior.
 */
public enum ConversationStatus {
    NORMAL, // Regular inbox — both users are friends or conversation was accepted
    PENDING, // Message request — awaiting receiver's acceptance (stranger messaging)
    BLOCKED // Receiver blocked the sender
}
