package iuh.fit.exception;

/**
 * Exception cho lỗi authorization (HTTP 403).
 * Sử dụng khi user đã đăng nhập nhưng không có quyền truy cập resource.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    /**
     * Helper methods cho authorization errors
     */
    public static ForbiddenException accessDenied() {
        return new ForbiddenException(ErrorCode.FORBIDDEN);
    }

    public static ForbiddenException cannotEditMessage() {
        return new ForbiddenException(ErrorCode.CANNOT_EDIT_MESSAGE);
    }

    public static ForbiddenException cannotDeleteMessage() {
        return new ForbiddenException(ErrorCode.CANNOT_DELETE_MESSAGE);
    }

    public static ForbiddenException notConversationMember() {
        return new ForbiddenException(
            ErrorCode.NOT_CONVERSATION_MEMBER,
            "Bạn phải là thành viên của hội thoại để thực hiện hành động này"
        );
    }

    public static ForbiddenException userInactive() {
        return new ForbiddenException(ErrorCode.USER_INACTIVE);
    }
}
