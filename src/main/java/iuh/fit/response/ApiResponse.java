package iuh.fit.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardized API response wrapper cho tất cả endpoints trong Fruvia Chat.
 * Đảm bảo format nhất quán cho cả success và error responses.
 * 
 * @param <T> Kiểu dữ liệu của response data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * Trạng thái success của request
     * true: Request thành công
     * false: Request thất bại (có error)
     */
    private boolean success;
    
    /**
     * Message mô tả kết quả (tiếng Việt)
     * VD: "Lấy thông tin user thành công", "Tạo tin nhắn thành công"
     */
    private String message;
    
    /**
     * Dữ liệu trả về (chỉ có khi success = true)
     */
    private T data;
    
    /**
     * Thông tin error (chỉ có khi success = false)
     */
    private ErrorInfo error;
    
    /**
     * Timestamp của response
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * Metadata bổ sung (pagination, filtering info...)
     */
    private Object metadata;

    // ==================== STATIC FACTORY METHODS ====================
    
    /**
     * Tạo success response với data
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message("Thành công")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Tạo success response với data và custom message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Tạo success response với data, message và metadata
     */
    public static <T> ApiResponse<T> success(T data, String message, Object metadata) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .metadata(metadata)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Tạo success response không có data (VD: DELETE operations)
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Tạo error response với error code và message
     */
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .error(new ErrorInfo(errorCode, null))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Tạo error response với error code, message và details
     */
    public static <T> ApiResponse<T> error(String errorCode, String message, Object details) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .error(new ErrorInfo(errorCode, details))
            .timestamp(LocalDateTime.now())
            .build();
    }

    // ==================== INNER CLASS ====================
    
    /**
     * Thông tin lỗi trong response
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class ErrorInfo {
        /**
         * Error code (VD: USER_NOT_FOUND, INVALID_TOKEN)
         */
        private String code;
        
        /**
         * Chi tiết bổ sung về lỗi
         */
        private Object details;
    }
}
