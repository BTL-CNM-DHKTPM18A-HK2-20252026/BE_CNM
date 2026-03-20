package iuh.fit.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Enum định nghĩa các error codes chuẩn cho Fruvia Chat.
 * Mỗi error code bao gồm HTTP status và message template.
 */

@Getter
public enum ErrorCode {
    // Authentication & Authorization - 401, 403
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token đã hết hạn, vui lòng đăng nhập lại"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"),
    
    // User - 404, 400, 409
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email này đã được đăng ký"),
    PHONE_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Số điện thoại này đã được đăng ký"),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),
    INVALID_USER_DATA(HttpStatus.BAD_REQUEST, "Dữ liệu người dùng không hợp lệ"),
    
    // Message - 404, 400
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"),
    MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống"),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Tin nhắn quá dài (tối đa 5000 ký tự)"),
    CANNOT_EDIT_MESSAGE(HttpStatus.FORBIDDEN, "Bạn chỉ có thể chỉnh sửa tin nhắn của mình"),
    CANNOT_DELETE_MESSAGE(HttpStatus.FORBIDDEN, "Bạn chỉ có thể xóa tin nhắn của mình"),
    
    // Conversation - 404, 400, 403
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại"),
    NOT_CONVERSATION_MEMBER(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của hội thoại này"),
    CANNOT_LEAVE_CONVERSATION(HttpStatus.BAD_REQUEST, "Không thể rời khỏi hội thoại"),
    
    // Friend - 404, 400, 409
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lời mời kết bạn"),
    ALREADY_FRIENDS(HttpStatus.CONFLICT, "Bạn đã là bạn bè với người này"),
    FRIEND_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "Lời mời kết bạn đã được gửi trước đó"),
    CANNOT_ADD_YOURSELF(HttpStatus.BAD_REQUEST, "Không thể tự gửi lời mời kết bạn cho chính mình"),
    
    // File Upload - 400, 413
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "File không được để trống"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File quá lớn"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "Loại file không được hỗ trợ"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Upload file thất bại"),
    
    // Validation - 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "Email không hợp lệ"),
    INVALID_PHONE(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ và số"),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "Thiếu trường bắt buộc"),
    
    // Server - 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi server, vui lòng thử lại sau"),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi khi truy cập cơ sở dữ liệu"),
    EXTERNAL_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ bên ngoài không khả dụng");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
