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
    USER_INACTIVE(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),
    USER_ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Người dùng này đã khóa tài khoản"),
    USER_PROFILE_PRIVATE(HttpStatus.FORBIDDEN, "Người dùng này không cho phép người lạ xem trang cá nhân"),
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
    NOT_GROUP_ADMIN(HttpStatus.FORBIDDEN, "Chỉ Trưởng nhóm mới có quyền thực hiện"),
    NOT_GROUP_CONVERSATION(HttpStatus.BAD_REQUEST, "Chức năng này chỉ áp dụng cho nhóm chat"),

    // Post & Reel - 404
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"),
    REEL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thước phim"),

    // Report - 400, 404
    REPORT_INVALID(HttpStatus.BAD_REQUEST, "Nội dung báo cáo không hợp lệ"),
    INVALID_MUTE_DURATION(HttpStatus.BAD_REQUEST, "Thời gian tắt thông báo không hợp lệ"),
    INVALID_AUTO_DELETE_DURATION(HttpStatus.BAD_REQUEST, "Thời gian tự xóa tin nhắn không hợp lệ"),

    // Friend - 404, 400, 409
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lời mời kết bạn"),
    ALREADY_FRIENDS(HttpStatus.CONFLICT, "Bạn đã là bạn bè với người này"),
    FRIEND_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "Lời mời kết bạn đã được gửi trước đó"),
    CANNOT_ADD_YOURSELF(HttpStatus.BAD_REQUEST, "Không thể tự gửi lời mời kết bạn cho chính mình"),
    USER_BLOCKED_YOU(HttpStatus.FORBIDDEN, "Bạn đã bị chặn bởi người dùng này"),
    FRIEND_REQUEST_ALREADY_FROM_RECEIVER(HttpStatus.CONFLICT, "Người dùng này đã gửi cho bạn một lời mời kết bạn"),
    FRIEND_REQUEST_NOT_PENDING(HttpStatus.BAD_REQUEST, "Lời mời kết bạn không ở trạng thái chờ xử lý"),
    NOT_AUTHORIZED_TO_HANDLE_REQUEST(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý lời mời kết bạn này"),
    MESSAGE_SENDER_ONLY(HttpStatus.FORBIDDEN, "Chỉ người gửi mới có thể thực hiện thao tác này"),
    MESSAGE_ALREADY_RECALLED(HttpStatus.BAD_REQUEST, "Tin nhắn đã được thu hồi"),
    MESSAGE_EDIT_TIME_EXCEEDED(HttpStatus.BAD_REQUEST, "Đã vượt quá thời gian cho phép chỉnh sửa tin nhắn"),
    MESSAGE_RECALL_TIME_EXCEEDED(HttpStatus.BAD_REQUEST, "Đã vượt quá thời gian cho phép thu hồi tin nhắn"),
    RECIPIENT_REQUIRED(HttpStatus.BAD_REQUEST, "RecipientId hoặc ConversationId là bắt buộc"),
    GROUP_MESSAGES_DISABLED(HttpStatus.FORBIDDEN, "Trưởng nhóm đã tắt tính năng gửi tin nhắn đối với thành viên"),
    STRANGERS_BLOCKED(HttpStatus.FORBIDDEN, "Người dùng này không nhận tin nhắn từ người lạ"),
    FRIENDS_ONLY_MESSAGES(HttpStatus.FORBIDDEN, "Người dùng này chỉ nhận tin nhắn từ bạn bè"),

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
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Mã OTP không hợp lệ"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn"),
    OTP_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy mã OTP, vui lòng yêu cầu gửi lại"),
    EMAIL_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "Email đã được xác thực"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email chưa được xác thực"),

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
