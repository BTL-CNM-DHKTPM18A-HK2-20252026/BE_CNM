package iuh.fit.exception;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cấu trúc response chuẩn cho tất cả errors trong Fruvia Chat.
 * Cung cấp thông tin đầy đủ để frontend xử lý errors hiệu quả.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * HTTP status code (400, 401, 404, 500...)
     */
    private int status;
    
    /**
     * Error code cụ thể để frontend identify lỗi
     * VD: USER_NOT_FOUND, INVALID_TOKEN, MESSAGE_TOO_LONG
     */
    private String errorCode;
    
    /**
     * Message mô tả lỗi bằng tiếng Việt (hiển thị cho user)
     */
    private String message;
    
    /**
     * Timestamp khi lỗi xảy ra
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * API path gây ra lỗi
     */
    private String path;
    
    /**
     * Chi tiết bổ sung (tùy chọn, chỉ hiển thị trong dev mode)
     */
    private Object details;

    /**
     * Constructor đơn giản cho errors không cần details
     */
    public ErrorResponse(int status, String errorCode, String message, String path) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.path = path;
    }

    /**
     * Constructor đầy đủ với details
     */
    public ErrorResponse(int status, String errorCode, String message, String path, Object details) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.path = path;
        this.details = details;
    }
}
