package iuh.fit.service.user;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.request.user.UpdateAvatarRequest;
import iuh.fit.dto.request.user.UpdateProfileRequest;
import iuh.fit.dto.response.user.UserMeResponse;
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

    /**
     * Get essential user information for current user
     * @param userId User ID
     * @return UserMeResponse
     */
    UserMeResponse getUserMe(String userId);

    /**
     * Update user profile information
     * @param userId User ID
     * @param request UpdateProfileRequest object
     * @return UserMeResponse with updated information
     */
    UserMeResponse updateProfile(String userId, UpdateProfileRequest request);

    /**
     * Update user avatar URL
     * @param userId User ID
     * @param request UpdateAvatarRequest object
     * @return UserMeResponse with updated information
     */
    UserMeResponse updateAvatar(String userId, UpdateAvatarRequest request);
}
