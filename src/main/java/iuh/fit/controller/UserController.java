package iuh.fit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.service.user.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
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
public class UserController {

    UserService userService;

    /**
     * Register a new user
     * 
     * @param request Registration request with user details
     * @return Created user response
     */
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
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        // TODO: Get userId from JWT token in SecurityContext
        log.info("Get current user profile request");
        // String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        // UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(UserResponse.builder()
                .userId("current-user-id")
                .email("user@example.com")
                .displayName("Current User")
                .build());
    }
}
