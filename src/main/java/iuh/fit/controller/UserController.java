package iuh.fit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "User Management", description = "API quản lý người dùng")
public class UserController {

    UserService userService;

    /**
     * Register a new user
     * 
     * @param request Registration request with user details
     * @return Created user response
     */
    @Operation(summary = "Đăng ký người dùng mới", description = "Tạo tài khoản người dùng mới với thông tin đầy đủ")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Đăng ký thành công",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Thông tin không hợp lệ hoặc email/số điện thoại đã tồn tại",
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
    @Operation(summary = "Lấy thông tin người dùng theo ID", description = "Trả về thông tin chi tiết của người dùng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tìm thấy người dùng",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng",
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
    @Operation(summary = "Lấy thông tin người dùng hiện tại", 
               description = "Trả về thông tin của người dùng đang đăng nhập (yêu cầu xác thực)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy thông tin thành công",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
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
