package iuh.fit.controller;

import java.text.ParseException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JOSEException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.auth.AuthenticationRequest;
import iuh.fit.dto.request.auth.IntrospectRequest;
import iuh.fit.dto.request.auth.LogoutRequest;
import iuh.fit.dto.response.auth.AuthenticationResponse;
import iuh.fit.dto.response.auth.IntrospectResponse;
import iuh.fit.service.auth.AuthenticationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication Controller
 * Handles all authentication-related endpoints including login, logout, and token validation
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Authentication", description = "API xác thực người dùng")
public class AuthenticationController {

    AuthenticationService authenticationService;

    /**
     * Login endpoint
     * Authenticates user with username/email and password
     * 
     * @param request Authentication request containing username and password
     * @return Authentication response with access token
     */    @Operation(summary = "Đăng nhập", description = "Xác thực người dùng và trả về JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thông tin đăng nhập không chính xác",
                    content = @Content)
    })    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) 
            throws JOSEException {
        log.info("Login attempt for user: {}", request.getUsername());
        AuthenticationResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Token introspection endpoint
     * Validates if the provided token is still valid
     * 
     * @param request Introspect request containing token
     * @return Introspect response with token validity status
     */
    @Operation(summary = "Xác thực token", description = "Kiểm tra tính hợp lệ của JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token hợp lệ",
                    content = @Content(schema = @Schema(implementation = IntrospectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Token không hợp lệ",
                    content = @Content)
    })
    @PostMapping("/introspect")
    public ResponseEntity<IntrospectResponse> introspect(@RequestBody IntrospectRequest request) 
            throws JOSEException, ParseException {
        IntrospectResponse response = authenticationService.introspect(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint
     * Invalidates the user's access token
     * 
     * @param request Logout request containing token
     * @return Success response
     */
    @Operation(summary = "Đăng xuất", description = "Vô hiệu hóa JWT token hiện tại")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @ApiResponse(responseCode = "400", description = "Token không hợp lệ",
                    content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return ResponseEntity.ok("Logged out successfully");
    }
}
