package iuh.fit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.request.user.UpdateAvatarRequest;
import iuh.fit.dto.request.user.UpdateCoverPhotoRequest;
import iuh.fit.dto.request.user.UpdateProfileRequest;
import iuh.fit.dto.response.user.UserMeResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.service.user.UserService;
import iuh.fit.response.ApiResponse;
import iuh.fit.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import iuh.fit.service.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * User Controller
 * Handles user registration and profile management
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "User Management", description = "User management APIs")
public class UserController {

    UserService userService;
    S3Service s3Service;

    /**
     * Register a new user
     * 
     * @param request Registration request with user details
     * @return Created user response
     */
    @Operation(summary = "Register new user", description = "Create a new user account with full information")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid information or email/phone already exists",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("User registration request received for email: {}", request.getEmail());
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get user by ID
     * 
     * @param userId User ID
     * @return User response
     */
    @Operation(summary = "Get user by ID", description = "Returns detailed information of a user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String userId) {
        log.info("Get user request for userId: {}", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user profile
     * This endpoint requires authentication
     * 
     * @return Current user response
     */
    @Operation(summary = "Get current user profile", 
               description = "Returns information of the currently logged-in user (requires authentication)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved profile",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getCurrentUser() {
        log.info("Get current user profile request");
        
        String userId = JwtUtils.getCurrentUserId();
        log.info("Extracted userId from JWT: {}", userId);
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UserMeResponse response = userService.getUserMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin cá nhân thành công"));
    }

    @Operation(summary = "Update current user profile", 
               description = "Update information of the currently logged-in user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(schema = @Schema(implementation = UserMeResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateProfile(
            @RequestBody UpdateProfileRequest request) {
        log.info("Update current user profile request");
        
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UserMeResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin cá nhân thành công"));
    }

    @Operation(summary = "Update current user avatar", 
               description = "Update only the avatar URL of the currently logged-in user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Avatar updated successfully",
                    content = @Content(schema = @Schema(implementation = UserMeResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/me/avatar")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateAvatar(
            @RequestBody UpdateAvatarRequest request) {
        log.info("Update current user avatar request");
        
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UserMeResponse response = userService.updateAvatar(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật ảnh đại diện thành công"));
    }

    @Operation(summary = "Update current user cover photo",
               description = "Update only the cover photo URL of the currently logged-in user")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/me/cover-photo")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateCoverPhoto(
            @RequestBody UpdateCoverPhotoRequest request) {
        log.info("Update current user cover photo request");

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserMeResponse response = userService.updateCoverPhoto(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật ảnh nền thành công"));
    }

    @Operation(summary = "Get presigned URL for profile photo upload",
               description = "Returns a pre-signed S3 URL to upload a profile/cover photo directly from the client")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me/presigned-url")
    public ResponseEntity<ApiResponse<String>> getProfilePresignedUrl(
            @org.springframework.web.bind.annotation.RequestParam String fileName,
            @org.springframework.web.bind.annotation.RequestParam String fileType) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String url = s3Service.generatePresignedUrlForProfile(fileName, fileType, userId);
        return ResponseEntity.ok(ApiResponse.success(url, "Lấy URL upload ảnh profile thành công"));
    }

    @Operation(summary = "Get user by phone number", description = "Find a user profile using their phone number")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    @GetMapping("/phone/{phoneNumber}")
    public ResponseEntity<UserResponse> getUserByPhoneNumber(@PathVariable String phoneNumber) {
        log.info("Get user by phone number request: {}", phoneNumber);
        String currentUserId = JwtUtils.getCurrentUserId();
        UserResponse response = userService.getUserByPhoneNumber(phoneNumber, currentUserId);
        return ResponseEntity.ok(response);
    }
}
