package iuh.fit.service.user;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.request.user.SetPinRequest;
import iuh.fit.dto.request.user.UpdateAvatarRequest;
import iuh.fit.dto.request.user.UpdateCoverPhotoRequest;
import iuh.fit.dto.request.user.UpdateProfileRequest;
import iuh.fit.dto.response.user.UserMeResponse;
import iuh.fit.dto.response.user.UserResponse;

public interface UserService {

    /**
     * Register a new user
     * 
     * @param request Registration request with user information
     * @return User response with created user details
     */
    UserResponse register(RegisterRequest request);

    /**
     * Get user by ID
     * 
     * @param userId        Target user ID
     * @param currentUserId Optional current user ID to check friendship status
     * @return UserResponse
     */
    UserResponse getUserById(String userId, String currentUserId);

    /**
     * Get essential user information for current user
     * 
     * @param userId User ID
     * @return UserMeResponse
     */
    UserMeResponse getUserMe(String userId);

    /**
     * Update user profile information
     * 
     * @param userId  User ID
     * @param request UpdateProfileRequest object
     * @return UserMeResponse with updated information
     */
    UserMeResponse updateProfile(String userId, UpdateProfileRequest request);

    /**
     * Update user avatar URL
     * 
     * @param userId  User ID
     * @param request UpdateAvatarRequest object
     * @return UserMeResponse with updated information
     */
    UserMeResponse updateAvatar(String userId, UpdateAvatarRequest request);

    /**
     * Update user cover photo URL
     * 
     * @param userId  User ID
     * @param request UpdateCoverPhotoRequest object
     * @return UserMeResponse with updated information
     */
    UserMeResponse updateCoverPhoto(String userId, UpdateCoverPhotoRequest request);

    /**
     * Get user information by email
     * 
     * @param email         Email to search for
     * @param currentUserId Optional current user ID to check friendship status
     * @return UserResponse
     */
    UserResponse getUserByEmail(String email, String currentUserId);

    /**
     * Get user information by phone number
     * 
     * @param phoneNumber   Phone number to search for
     * @param currentUserId Optional current user ID to check friendship status
     * @return UserResponse
     */
    UserResponse getUserByPhone(String phoneNumber, String currentUserId);

    /**
     * Set or change the 6-digit PIN for hidden conversations.
     * If a PIN already exists, currentPin must be provided and correct.
     *
     * @param userId  authenticated user ID
     * @param request SetPinRequest containing new PIN and optional current PIN
     */
    void setupPin(String userId, SetPinRequest request);

    /**
     * Check whether the user has already configured a PIN.
     *
     * @param userId authenticated user ID
     * @return true if pinCode is set
     */
    boolean hasPinConfigured(String userId);

    /**
     * Verify a raw PIN against the stored bcrypt hash.
     *
     * @param userId authenticated user ID
     * @param rawPin plain-text 6-digit PIN
     * @return true if matches
     */
    boolean verifyPin(String userId, String rawPin);
}
