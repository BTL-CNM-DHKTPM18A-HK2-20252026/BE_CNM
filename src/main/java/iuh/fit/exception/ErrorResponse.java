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
    
    private int status;
    private String errorCode;
    private String message;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    private String path;
    private Object details;

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Object getDetails() { return details; }
    public void setDetails(Object details) { this.details = details; }

    public ErrorResponse(int status, String errorCode, String message, String path) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.path = path;
    }

    public ErrorResponse(int status, String errorCode, String message, String path, Object details) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.path = path;
        this.details = details;
    }

    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    public static class ErrorResponseBuilder {
        private final ErrorResponse response = new ErrorResponse();

        public ErrorResponseBuilder status(int status) { response.setStatus(status); return this; }
        public ErrorResponseBuilder errorCode(String errorCode) { response.setErrorCode(errorCode); return this; }
        public ErrorResponseBuilder message(String message) { response.setMessage(message); return this; }
        public ErrorResponseBuilder timestamp(LocalDateTime timestamp) { response.setTimestamp(timestamp); return this; }
        public ErrorResponseBuilder path(String path) { response.setPath(path); return this; }
        public ErrorResponseBuilder details(Object details) { response.setDetails(details); return this; }
        public ErrorResponse build() { return response; }
    }
}
