package iuh.fit.exception;

/**
 * Exception cho lỗi authentication (HTTP 401).
 * Sử dụng khi user chưa đăng nhập hoặc token không hợp lệ.
 */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UnauthorizedException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    /**
     * Helper methods cho authentication errors
     */
    public static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
    }

    public static UnauthorizedException invalidToken() {
        return new UnauthorizedException(ErrorCode.INVALID_TOKEN);
    }

    public static UnauthorizedException tokenExpired() {
        return new UnauthorizedException(ErrorCode.TOKEN_EXPIRED);
    }

    public static UnauthorizedException notAuthenticated() {
        return new UnauthorizedException(
            ErrorCode.UNAUTHORIZED,
            "Bạn cần đăng nhập để thực hiện hành động này"
        );
    }
}
