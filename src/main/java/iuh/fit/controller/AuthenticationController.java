package iuh.fit.controller;

import java.text.ParseException;

import org.springframework.http.ResponseEntity;
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
import iuh.fit.dto.request.auth.CheckPhoneNumberRequest;
import iuh.fit.dto.request.auth.IntrospectRequest;
import iuh.fit.dto.request.auth.LogoutRequest;
import iuh.fit.dto.request.auth.QrConfirmRequest;
import iuh.fit.dto.response.auth.AuthenticationResponse;
import iuh.fit.dto.response.auth.IntrospectResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.auth.AuthenticationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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

    AuthenticationService authenticationService;

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
    public ResponseEntity<ApiResponse<AuthenticationResponse>> authenticate(@RequestBody AuthenticationRequest request)
            throws JOSEException {
        log.info("Login attempt for user: {}", request.getUsername());
        log.info("Login attempt for user: {}", request);
        AuthenticationResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đăng nhập thành công"));
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
     * Check if a phone number exists
     * 
     * @param request Request containing phone number
     * @return true if exists, false otherwise
     */
    @Operation(summary = "Check phone number", description = "Verify if a phone number exists in the system")
    @PostMapping("/check-phone-number")
    public ResponseEntity<ApiResponse<Boolean>> checkPhoneNumber(@RequestBody CheckPhoneNumberRequest request) {
        boolean exists = authenticationService.checkPhoneNumberExists(request.getPhoneNumber());
        return ResponseEntity.ok(ApiResponse.success(exists, "Kiểm tra số điện thoại thành công"));
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
}
