package iuh.fit.controller;

import java.text.ParseException;
import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JOSEException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.auth.AuthenticationRequest;
import iuh.fit.dto.request.auth.CheckEmailRequest;
import iuh.fit.dto.request.auth.ForgotPasswordRequest;
import iuh.fit.dto.request.auth.IntrospectRequest;
import iuh.fit.dto.request.auth.LogoutRequest;
import iuh.fit.dto.request.auth.QrConfirmRequest;
import iuh.fit.dto.request.auth.ResendOtpRequest;
import iuh.fit.dto.request.auth.ResetPasswordRequest;
import iuh.fit.dto.request.auth.VerifyOtpRequest;
import iuh.fit.dto.response.auth.AuthenticationResponse;
import iuh.fit.dto.response.auth.IntrospectResponse;
import iuh.fit.entity.UserDevice;
import iuh.fit.repository.UserDeviceRepository;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.auth.AuthenticationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication Controller
 * Handles all authentication-related endpoints including login, logout, and
 * token validation
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Authentication", description = "User authentication APIs")
public class AuthenticationController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthenticationController.class);

    AuthenticationService authenticationService;
    UserDeviceRepository userDeviceRepository;

    @NonFinal
    @Value("${jwt.refresh-token.cookie-name}")
    String refreshTokenCookieName;

    @NonFinal
    @Value("${jwt.refresh-token.cookie-secure}")
    boolean refreshTokenCookieSecure;

    @NonFinal
    @Value("${jwt.refresh-token.cookie-same-site}")
    String refreshTokenCookieSameSite;

    @NonFinal
    @Value("${jwt.refresh-token.expiration}")
    long refreshTokenExpiration;

    /**
     * Login endpoint
     * Authenticates user with username/email and password
     * 
     * @param request Authentication request containing username and password
     * @return Authentication response with access token
     */
    @Operation(summary = "Login", description = "Authenticate user and return JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> authenticate(
            @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest)
            throws JOSEException {
        log.info("Login attempt for user: {}", request.getUsername());
        AuthenticationResponse response = authenticationService.authenticate(request);

        // Record device info on successful login
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            String ip = httpRequest.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank())
                ip = httpRequest.getRemoteAddr();

            String browser = parseBrowser(userAgent);
            String os = parseOS(userAgent);
            String deviceType = parseDeviceType(userAgent);
            String deviceName = browser + " on " + os;

            // Extract userId from the access token
            String userId = extractUserIdFromToken(response.getAccessToken());

            if (userId != null) {
                // Check if same device already exists for this user
                java.util.Optional<UserDevice> existingDevice = userDeviceRepository
                        .findByUserIdAndDeviceNameAndIpAddressAndIsActiveTrue(userId, deviceName, ip);

                if (existingDevice.isPresent()) {
                    // Update existing device record
                    UserDevice device = existingDevice.get();
                    device.setLastActiveAt(java.time.LocalDateTime.now());
                    device.setLoginAt(java.time.LocalDateTime.now());
                    userDeviceRepository.save(device);
                    log.info("Updated existing device login for user {}: {}", userId, deviceName);
                } else {
                    // Create new device record
                    UserDevice device = UserDevice.builder()
                            .userId(userId)
                            .deviceName(deviceName)
                            .deviceType(deviceType)
                            .browser(browser)
                            .os(os)
                            .ipAddress(ip)
                            .loginAt(java.time.LocalDateTime.now())
                            .lastActiveAt(java.time.LocalDateTime.now())
                            .createdAt(java.time.LocalDateTime.now())
                            .isActive(true)
                            .build();
                    userDeviceRepository.save(device);
                    log.info("Recorded new device login for user {}: {}", userId, deviceName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record device info: {}", e.getMessage());
        }

        // Mobile clients send X-Platform header and cannot handle cookies
        String platform = httpRequest.getHeader("X-Platform");
        boolean isMobile = "mobile".equalsIgnoreCase(platform);

        if (isMobile) {
            // Return refresh token in response body for mobile
            return ResponseEntity.ok()
                    .body(ApiResponse.success(response, "Đăng nhập thành công"));
        }

        // Web clients: set refresh token as HttpOnly cookie
        ResponseCookie refreshTokenCookie = buildRefreshTokenCookie(response.getRefreshToken());
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ApiResponse.success(response, "Đăng nhập thành công"));
    }

    @Operation(summary = "Refresh access token", description = "Issue a new access token from refresh token cookie or request body")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Refresh successful", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid refresh token", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refreshToken(
            @RequestBody(required = false) java.util.Map<String, String> body,
            HttpServletRequest request)
            throws JOSEException, ParseException {
        // Try from request body first (mobile), then fall back to cookie (web)
        String refreshToken = null;
        if (body != null && body.containsKey("refresh_token")) {
            refreshToken = body.get("refresh_token");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = extractRefreshTokenFromCookies(request);
        }
        AuthenticationResponse response = authenticationService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(response, "Làm mới access token thành công"));
    }

    /**
     * Token introspection endpoint
     * Validates if the provided token is still valid
     * 
     * @param request Introspect request containing token
     * @return Introspect response with token validity status
     */
    @Operation(summary = "Introspect token", description = "Verify the validity of a JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Valid token", content = @Content(schema = @Schema(implementation = IntrospectResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid token", content = @Content)
    })
    @PostMapping("/introspect")
    public ResponseEntity<ApiResponse<IntrospectResponse>> introspect(@RequestBody IntrospectRequest request)
            throws JOSEException, ParseException {
        IntrospectResponse response = authenticationService.introspect(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Kiểm tra token thành công"));
    }

    /**
     * Logout endpoint
     * Invalidates the user's access token
     * 
     * @param request Logout request containing token
     * @return Success response
     */
    @Operation(summary = "Logout", description = "Invalidate the current JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid token", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công"));
    }

    /**
     * Check if an email exists
     * 
     * @param request Request containing email
     * @return true if exists, false otherwise
     */
    @Operation(summary = "Check email", description = "Verify if an email exists in the system")
    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>> checkEmail(@RequestBody CheckEmailRequest request) {
        boolean exists = authenticationService.checkEmailExists(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(exists, "Kiểm tra email thành công"));
    }

    /**
     * Check if a phone number exists
     * 
     * @param phoneNumber Phone number to check
     * @return true if exists, false otherwise
     */
    @Operation(summary = "Check phone", description = "Verify if a phone number exists in the system")
    @PostMapping("/check-phone")
    public ResponseEntity<ApiResponse<Boolean>> checkPhone(@RequestBody java.util.Map<String, String> request) {
        boolean exists = authenticationService.checkPhoneExists(request.get("phoneNumber"));
        return ResponseEntity.ok(ApiResponse.success(exists, "Kiểm tra số điện thoại thành công"));
    }

    @Operation(summary = "Verify email OTP", description = "Verify a 6-digit OTP for email verification")
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authenticationService.verifyEmailOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Xác thực email thành công"));
    }

    @Operation(summary = "Send registration OTP", description = "Send a 6-digit OTP for registration pre-verification")
    @PostMapping("/register/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendRegistrationOtp(@Valid @RequestBody ResendOtpRequest request) {
        authenticationService.sendRegistrationOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Đã gửi mã OTP đăng ký"));
    }

    @Operation(summary = "Verify registration OTP", description = "Verify a 6-digit OTP for registration pre-verification")
    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyRegistrationOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authenticationService.verifyRegistrationOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Xác thực OTP đăng ký thành công"));
    }

    @Operation(summary = "Resend email OTP", description = "Resend a new 6-digit OTP code to the target email")
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authenticationService.resendEmailOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lại mã OTP"));
    }

    @Operation(summary = "Send forgot-password OTP", description = "Send a 6-digit OTP code for password reset")
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendForgotPasswordOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.sendPasswordResetOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Đã gửi mã OTP đặt lại mật khẩu"));
    }

    @Operation(summary = "Reset password", description = "Reset account password using email + OTP")
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công"));
    }

    private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(refreshTokenCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshTokenCookieSecure)
                .path("/")
                .maxAge(Duration.ofSeconds(refreshTokenExpiration))
                .sameSite(refreshTokenCookieSameSite)
                .build();
    }

    private String extractUserIdFromToken(String token) {
        try {
            com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(token);
            return jwt.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private String parseBrowser(String ua) {
        if (ua == null)
            return "Unknown";
        if (ua.contains("Edg/"))
            return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera"))
            return "Opera";
        if (ua.contains("Chrome/") && !ua.contains("Edg/"))
            return "Chrome";
        if (ua.contains("Safari/") && !ua.contains("Chrome/"))
            return "Safari";
        if (ua.contains("Firefox/"))
            return "Firefox";
        return "Unknown";
    }

    private String parseOS(String ua) {
        if (ua == null)
            return "Unknown";
        if (ua.contains("Windows NT 10"))
            return "Windows 10";
        if (ua.contains("Windows NT 11") || (ua.contains("Windows NT 10") && ua.contains("Win64")))
            return "Windows";
        if (ua.contains("Windows"))
            return "Windows";
        if (ua.contains("Mac OS X"))
            return "macOS";
        if (ua.contains("Android"))
            return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad"))
            return "iOS";
        if (ua.contains("Linux"))
            return "Linux";
        return "Unknown";
    }

    private String parseDeviceType(String ua) {
        if (ua == null)
            return "WEB";
        if (ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone"))
            return "MOBILE";
        if (ua.contains("Electron") || ua.contains("Desktop"))
            return "DESKTOP";
        return "WEB";
    }

    private String extractRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (refreshTokenCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    /**
     * Get a unique QR session UUID
     *
     * @return UUID string
     */
    @Operation(summary = "Get QR session", description = "Get a unique UUID for QR login, valid for 120s")
    @GetMapping("/qr-session")
    public ResponseEntity<ApiResponse<String>> getQrSession() {
        log.info("Request for a new QR session");
        String uuid = authenticationService.generateQrSession();
        return ResponseEntity.ok(ApiResponse.success(uuid, "Tạo phiên QR thành công"));
    }

    /**
     * Confirm QR login from mobile app
     *
     * @param request Request containing UUID and User ID
     * @return Success response
     * @throws JOSEException if token generation fails
     */
    @Operation(summary = "Confirm QR Login", description = "Called by Mobile App to authorize a QR login session")
    @PostMapping("/qr-confirm")
    public ResponseEntity<ApiResponse<Void>> confirmQr(@RequestBody QrConfirmRequest request)
            throws JOSEException {
        log.info("Mobile app confirmed QR login for session: {}", request.getUuid());
        authenticationService.qrConfirm(request.getUuid(), request.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Xác nhận đăng nhập thành công"));
    }

    @Operation(summary = "Notify QR Scanned", description = "Called by Mobile App when a QR code is first scanned")
    @PostMapping("/qr-scan")
    public ResponseEntity<ApiResponse<Void>> scanQr(@RequestBody QrConfirmRequest request)
            throws JOSEException {
        log.info("Mobile app scanned QR login for session: {}", request.getUuid());
        authenticationService.qrScanned(request.getUuid(), request.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Thông báo quét mã thành công"));
    }

    @GetMapping("/test-cicd")
    public ResponseEntity<ApiResponse<String>> testCicd() {
        return ResponseEntity.ok(ApiResponse.success("Chúc mừng bạn đã CICD thành công 123"));
    }
}
