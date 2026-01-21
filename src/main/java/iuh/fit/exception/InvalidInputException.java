package iuh.fit.exception;

/**
 * Exception cho dữ liệu đầu vào không hợp lệ (HTTP 400).
 * Sử dụng cho validation failures, invalid format, business rules violations.
 */
public class InvalidInputException extends AppException {

    public InvalidInputException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidInputException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public InvalidInputException(ErrorCode errorCode, String customMessage, Object details) {
        super(errorCode, customMessage, details);
    }

    /**
     * Helper methods cho các validation thường gặp
     */
    public static InvalidInputException invalidEmail(String email) {
        return new InvalidInputException(
            ErrorCode.INVALID_EMAIL,
            "Email không hợp lệ: " + email
        );
    }

    public static InvalidInputException invalidPhone(String phone) {
        return new InvalidInputException(
            ErrorCode.INVALID_PHONE,
            "Số điện thoại không hợp lệ: " + phone
        );
    }

    public static InvalidInputException invalidPassword() {
        return new InvalidInputException(ErrorCode.INVALID_PASSWORD);
    }

    public static InvalidInputException missingField(String fieldName) {
        return new InvalidInputException(
            ErrorCode.MISSING_REQUIRED_FIELD,
            "Thiếu trường bắt buộc: " + fieldName
        );
    }

    public static InvalidInputException messageTooLong() {
        return new InvalidInputException(ErrorCode.MESSAGE_TOO_LONG);
    }

    public static InvalidInputException messageEmpty() {
        return new InvalidInputException(ErrorCode.MESSAGE_EMPTY);
    }
}
