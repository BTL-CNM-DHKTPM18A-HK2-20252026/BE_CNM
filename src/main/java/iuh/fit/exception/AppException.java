package iuh.fit.exception;

import lombok.Getter;

/**
 * Base exception class cho tất cả custom exceptions trong Fruvia Chat.
 * Sử dụng ErrorCode để định nghĩa loại lỗi và message.
 */
@Getter
public class AppException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Object details;

    /**
     * Constructor với ErrorCode
     */
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Constructor với ErrorCode và custom message
     */
    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Constructor với ErrorCode, custom message và details
     */
    public AppException(ErrorCode errorCode, String customMessage, Object details) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Constructor với ErrorCode và cause
     */
    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }
}
