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
    
    private boolean success;
    private String message;
    private T data;
    private ErrorInfo error;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    private Object metadata;

    // Manual getters/setters to bypass Lombok issues
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public ErrorInfo getError() { return error; }
    public void setError(ErrorInfo error) { this.error = error; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Object getMetadata() { return metadata; }
    public void setMetadata(Object metadata) { this.metadata = metadata; }

    // ==================== STATIC FACTORY METHODS ====================
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Thành công");
        response.setData(data);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = success(data);
        response.setMessage(message);
        return response;
    }
    
    public static <T> ApiResponse<T> success(T data, String message, Object metadata) {
        ApiResponse<T> response = success(data, message);
        response.setMetadata(metadata);
        return response;
    }
    
    public static <T> ApiResponse<T> success(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setError(new ErrorInfo(errorCode, null));
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    public static <T> ApiResponse<T> error(String errorCode, String message, Object details) {
        ApiResponse<T> response = error(errorCode, message);
        response.getError().setDetails(details);
        return response;
    }

    // Manual static builder
    public static <T> ApiResponseBuilder<T> builder() {
        return new ApiResponseBuilder<>();
    }

    public static class ApiResponseBuilder<T> {
        private final ApiResponse<T> response = new ApiResponse<>();

        public ApiResponseBuilder<T> success(boolean success) { response.setSuccess(success); return this; }
        public ApiResponseBuilder<T> message(String message) { response.setMessage(message); return this; }
        public ApiResponseBuilder<T> data(T data) { response.setData(data); return this; }
        public ApiResponseBuilder<T> error(ErrorInfo error) { response.setError(error); return this; }
        public ApiResponseBuilder<T> timestamp(LocalDateTime timestamp) { response.setTimestamp(timestamp); return this; }
        public ApiResponseBuilder<T> metadata(Object metadata) { response.setMetadata(metadata); return this; }
        public ApiResponse<T> build() { return response; }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class ErrorInfo {
        private String code;
        private Object details;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public Object getDetails() { return details; }
        public void setDetails(Object details) { this.details = details; }
    }
}
