package iuh.fit.enums;

/**
 * Processing state for AI-generated messages.
 */
public enum AiMessageStatus {
    PENDING,
    STREAMING,
    COMPLETED,
    FAILED,
    TIMEOUT
}
