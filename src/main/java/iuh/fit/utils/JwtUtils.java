package iuh.fit.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT utility methods for extracting user information
 */
public class JwtUtils {

    /**
     * Get current authenticated user ID from JWT token
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject(); // "sub" claim contains user ID
        }
        
        return null;
    }

    /**
     * Get current username from JWT token
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("username");
        }
        
        return null;
    }

    /**
     * Get user's roles from JWT token
     */
    public static String getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("scope");
        }
        
        return null;
    }

    /**
     * Check if current user has a specific role
     */
    public static boolean hasRole(String role) {
        String roles = getCurrentUserRoles();
        return roles != null && roles.contains(role);
    }

    /**
     * Get full JWT object
     */
    public static Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        
        return null;
    }

    /**
     * Check if user is authenticated
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
