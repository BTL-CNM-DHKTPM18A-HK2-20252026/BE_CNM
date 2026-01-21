package iuh.fit.exception;

/**
 * Exception cho trường hợp resource không tìm thấy (HTTP 404).
 * Sử dụng cho User, Message, Conversation không tồn tại.
 */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    /**
     * Helper method để tạo exception với resource ID
     */
    public static ResourceNotFoundException user(String userId) {
        return new ResourceNotFoundException(
            ErrorCode.USER_NOT_FOUND,
            "Không tìm thấy người dùng với ID: " + userId
        );
    }

    public static ResourceNotFoundException message(String messageId) {
        return new ResourceNotFoundException(
            ErrorCode.MESSAGE_NOT_FOUND,
            "Không tìm thấy tin nhắn với ID: " + messageId
        );
    }

    public static ResourceNotFoundException conversation(String conversationId) {
        return new ResourceNotFoundException(
            ErrorCode.CONVERSATION_NOT_FOUND,
            "Không tìm thấy hội thoại với ID: " + conversationId
        );
    }

    public static ResourceNotFoundException friendRequest(String requestId) {
        return new ResourceNotFoundException(
            ErrorCode.FRIEND_REQUEST_NOT_FOUND,
            "Không tìm thấy lời mời kết bạn với ID: " + requestId
        );
    }
}
