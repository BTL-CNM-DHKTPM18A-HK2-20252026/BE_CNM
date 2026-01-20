package iuh.fit.service.user;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.response.user.UserResponse;

public interface UserService {
    
    /**
     * Register a new user
     * @param request Registration request with user information
     * @return User response with created user details
     */
    UserResponse register(RegisterRequest request);
    
    /**
     * Get user by ID
     * @param userId User ID
     * @return User response
     */
    UserResponse getUserById(String userId);
}
