package iuh.fit.controller;

import com.nimbusds.jose.JOSEException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;

/**
 * Authentication Controller
 * Handles all authentication-related endpoints including login, logout, and token validation
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationController {

    AuthenticationService authenticationService;

    /**
     * Login endpoint
     * Authenticates user with username/email and password
     * 
     * @param request Authentication request containing username and password
     * @return Authentication response with access token
     */
    @PostMapping("/login")
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
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return ResponseEntity.ok("Logged out successfully");
    }
}
