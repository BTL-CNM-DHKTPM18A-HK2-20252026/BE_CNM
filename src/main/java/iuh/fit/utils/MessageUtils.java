package iuh.fit.utils;

/**
 * Message processing utility methods for Fruvia Chat
 */
public class MessageUtils {

    private static final int MAX_PREVIEW_LENGTH = 100;

    /**
     * Generate message preview for conversation list
     * @param content Full message content
     * @return Truncated preview with "..." if needed
     */
    public static String generatePreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String trimmed = content.trim();
        if (trimmed.length() <= MAX_PREVIEW_LENGTH) {
            return trimmed;
        }

        return trimmed.substring(0, MAX_PREVIEW_LENGTH) + "...";
    }

    /**
     * Generate message preview with type indicator
     * @param content Message content
     * @param messageType Type of message (TEXT, IMAGE, FILE, etc.)
     * @return Preview with icon/prefix
     */
    public static String generatePreviewWithType(String content, String messageType) {
        if (messageType == null) {
            return generatePreview(content);
        }

        return switch (messageType.toUpperCase()) {
            case "IMAGE" -> "📷 Hình ảnh";
            case "FILE" -> "📎 Tệp đính kèm";
            case "VOICE" -> "🎤 Tin nhắn thoại";
            case "VIDEO" -> "🎥 Video";
            case "LOCATION" -> "📍 Vị trí";
            case "STICKER" -> "😊 Sticker";
            default -> generatePreview(content);
        };
    }

    /**
     * Check if message contains URL
     */
    public static boolean containsUrl(String message) {
        if (message == null) {
            return false;
        }
        return message.matches(".*https?://.*");
    }

    /**
     * Extract URLs from message
     */
    public static String[] extractUrls(String message) {
        if (message == null || !containsUrl(message)) {
            return new String[0];
        }
        
        // Simple URL extraction (can be improved with regex)
        return message.split("\\s+");
    }

    /**
     * Check if message mentions user (@username)
     */
    public static boolean containsMention(String message, String username) {
        if (message == null || username == null) {
            return false;
        }
        return message.contains("@" + username);
    }

    /**
     * Count words in message
     */
    public static int countWords(String message) {
        if (message == null || message.isBlank()) {
            return 0;
        }
        return message.trim().split("\\s+").length;
    }

    /**
     * Truncate message to specific length
     */
    public static String truncate(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "...";
    }

    /**
     * Check if message is empty or only contains whitespace
     */
    public static boolean isEmpty(String message) {
        return message == null || message.isBlank();
    }
}
