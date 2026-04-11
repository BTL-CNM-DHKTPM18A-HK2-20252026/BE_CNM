package iuh.fit.enums;

/**
 * Enum for verification types
 */
public enum VerificationType {
    REGISTRATION, // Pre-registration email OTP verification
    EMAIL, // Email verification
    PASSWORD_RESET, // Forgot-password OTP verification
    PHONE, // Phone verification
    TWO_FA // Two-factor authentication
}
