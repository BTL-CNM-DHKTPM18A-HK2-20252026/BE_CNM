package iuh.fit.service.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import iuh.fit.dto.request.auth.AuthenticationRequest;
import iuh.fit.dto.request.auth.IntrospectRequest;
import iuh.fit.dto.request.auth.LogoutRequest;
import iuh.fit.dto.response.auth.AuthenticationResponse;
import iuh.fit.dto.response.auth.IntrospectResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Implementation of AuthenticationService for JWT-based authentication.
 * This service handles JWT token generation, validation, and user authentication.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthenticationServiceImpl implements AuthenticationService {

    // TODO: Inject necessary repositories here
    // UserRepository userRepository;
    // PasswordEncoder passwordEncoder;
    // RedisTokenService redisTokenService; // Optional: for token blacklisting

    @NonFinal
    @Value("${jwt.signer-key}")
    protected String SIGNER_KEY;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) throws JOSEException {
        // TODO: Implement authentication logic
        // 1. Find user by username or email
        // 2. Verify password
        // 3. Generate JWT token
        
        log.info("Authenticating user: {}", request.getUsername());
        
        // Example implementation (replace with actual logic)
        /*
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new RuntimeException("Invalid credentials");
        }
        */
        
        // Generate token (example with dummy user)
        String accessToken = generateToken("user-id", "username", "ROLE_USER", 30, ChronoUnit.DAYS);
        
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .expiresIn(30 * 24 * 3600) // 30 days in seconds
                .tokenType("Bearer")
                .build();
    }

    @Override
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        String token = request.getAccessToken();
        
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            // Verify signature
            JWSVerifier jwsVerifier = new MACVerifier(SIGNER_KEY.getBytes());
            boolean verified = signedJWT.verify(jwsVerifier);
            
            // TODO: Check Redis blacklist if implemented
            // if (redisTokenService.isTokenBlacklisted(signedJWT.getJWTClaimsSet().getJWTID())) {
            //     verified = false;
            // }
            
            // Check expiration
            Date expiredTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            boolean notExpired = expiredTime != null && expiredTime.after(new Date());
            
            return IntrospectResponse.builder()
                    .valid(verified && notExpired)
                    .build();
                    
        } catch (Exception e) {
            log.error("Token introspection failed", e);
            return IntrospectResponse.builder()
                    .valid(false)
                    .build();
        }
    }

    @Override
    public void logout(LogoutRequest request) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(request.getAccessToken());
            String tokenId = signedJWT.getJWTClaimsSet().getJWTID();
            Date expireAt = signedJWT.getJWTClaimsSet().getExpirationTime();
            
            // TODO: Implement token blacklisting with Redis
            // long ttlMillis = expireAt.getTime() - System.currentTimeMillis();
            // if (ttlMillis > 0) {
            //     redisTokenService.blacklistToken(tokenId, Duration.ofMillis(ttlMillis));
            //     log.info("Token {} blacklisted, expires in {}ms", tokenId, ttlMillis);
            // }
            
            log.info("User logged out successfully. Token ID: {}", tokenId);
            
        } catch (ParseException e) {
            log.error("Failed to parse token during logout", e);
            throw new RuntimeException("Invalid token");
        }
    }

    /**
     * Generate JWT token with user information
     * 
     * @param userId User ID to be set as subject
     * @param username Username to be included in claims
     * @param roles User roles (space-separated string)
     * @param timeAmount Time amount for token expiration
     * @param chronoUnit Time unit for token expiration
     * @return Generated JWT token string
     * @throws JOSEException if token generation fails
     */
    private String generateToken(String userId, String username, String roles, 
                                 long timeAmount, ChronoUnit chronoUnit) throws JOSEException {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("scope", roles) // Roles for Spring Security
                .claim("username", username)
                .issuer("Fruvia")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(timeAmount, chronoUnit)))
                .jwtID(UUID.randomUUID().toString())
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(jwsHeader, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw e;
        }
    }
}
