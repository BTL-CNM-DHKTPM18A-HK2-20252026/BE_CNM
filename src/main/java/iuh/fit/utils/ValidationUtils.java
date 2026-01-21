package iuh.fit.utils;

import java.util.regex.Pattern;

/**
 * Validation utility methods for Fruvia Chat
 */
public class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^(\\+84|0)[3|5|7|8|9][0-9]{8}$");
    
    private static final Pattern USERNAME_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate Vietnamese phone number
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Validate username (alphanumeric + underscore, 3-20 chars)
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Validate password strength (min 8 chars, at least 1 letter, 1 number)
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasLetter = false;
        boolean hasDigit = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        
        return hasLetter && hasDigit;
    }

    /**
     * Sanitize chat message (prevent XSS)
     */
    public static String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        
        return message
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
            .trim();
    }

    /**
     * Check if string is null or blank
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Check if string has valid length
     */
    public static boolean isValidLength(String str, int minLength, int maxLength) {
        if (str == null) {
            return false;
        }
        int length = str.trim().length();
        return length >= minLength && length <= maxLength;
    }

    /**
     * Validate message content (1-5000 chars)
     */
    public static boolean isValidMessageContent(String content) {
        return !isNullOrBlank(content) && isValidLength(content, 1, 5000);
    }
}
