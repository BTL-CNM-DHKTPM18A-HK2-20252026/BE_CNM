package iuh.fit.service.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import iuh.fit.dto.request.auth.AuthenticationRequest;
import iuh.fit.dto.request.auth.IntrospectRequest;
import iuh.fit.dto.request.auth.LogoutRequest;
import iuh.fit.dto.request.auth.ResetPasswordRequest;
import iuh.fit.dto.request.auth.VerifyOtpRequest;
import iuh.fit.dto.response.auth.AuthenticationResponse;
import iuh.fit.dto.response.auth.IntrospectResponse;
import iuh.fit.entity.UserAuth;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.response.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Implementation of AuthenticationService for JWT-based authentication.
 * This service handles JWT token generation, validation, and user
 * authentication.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String DEFAULT_ROLE = "ROLE_USER";

    UserAuthRepository userAuthRepository;
    UserDetailRepository userDetailRepository;
    PasswordEncoder passwordEncoder;
    QrSessionService qrSessionService;
    SimpMessagingTemplate messagingTemplate;
    EmailVerificationService emailVerificationService;
    iuh.fit.service.presence.SessionService sessionService;

    @NonFinal
    @Value("${jwt.access-token.secret}")
    protected String accessTokenSecret;

    @NonFinal
    @Value("${jwt.refresh-token.secret}")
    protected String refreshTokenSecret;

    @NonFinal
    @Value("${jwt.access-token.expiration}")
    protected long accessTokenExpiration;

    @NonFinal
    @Value("${jwt.refresh-token.expiration}")
    protected long refreshTokenExpiration;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) throws JOSEException {
        log.info("Authenticating user: {}", request.getUsername());

        UserAuth user = userAuthRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // Fetch user display name
        var userDetail = userDetailRepository.findById(user.getUserId()).orElse(null);
        String nameToInToken = (userDetail != null && userDetail.getDisplayName() != null)
                ? userDetail.getDisplayName()
                : user.getEmail();

        // Generate token with name as "username" claim
        String accessToken = generateToken(
                user.getUserId(),
                nameToInToken,
                DEFAULT_ROLE,
                accessTokenExpiration,
                ACCESS_TOKEN_TYPE,
                accessTokenSecret);

        String refreshToken = generateToken(
                user.getUserId(),
                nameToInToken,
                DEFAULT_ROLE,
                refreshTokenExpiration,
                REFRESH_TOKEN_TYPE,
                refreshTokenSecret);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiration)
                .tokenType("Bearer")
                .build();
    }

    @Override
    public AuthenticationResponse refreshAccessToken(String refreshToken) throws JOSEException, ParseException {
        JWTClaimsSet refreshClaims = verifyToken(refreshToken, refreshTokenSecret, REFRESH_TOKEN_TYPE);

        String userId = refreshClaims.getSubject();
        String username = refreshClaims.getStringClaim("username");
        String roles = refreshClaims.getStringClaim("scope");

        if (roles == null || roles.isBlank()) {
            roles = DEFAULT_ROLE;
        }

        String newAccessToken = generateToken(
                userId,
                username,
                roles,
                accessTokenExpiration,
                ACCESS_TOKEN_TYPE,
                accessTokenSecret);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(accessTokenExpiration)
                .tokenType("Bearer")
                .build();
    }

    @Override
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        String token = request.getAccessToken();
        if (token == null || token.isEmpty()) {
            return IntrospectResponse.builder().valid(false).build();
        }

        try {
            verifyToken(token, accessTokenSecret, ACCESS_TOKEN_TYPE);

            return IntrospectResponse.builder()
                    .valid(true)
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
        String token = request.getAccessToken();
        if (token == null || token.isEmpty()) {
            log.error("Token is null or empty during logout");
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        try {
            JWTClaimsSet claims = verifyToken(token, accessTokenSecret, ACCESS_TOKEN_TYPE);
            String tokenId = claims.getJWTID();
            Date expireAt = claims.getExpirationTime();
            String userId = claims.getSubject();

            // Layer 1: Xóa phiên kết nối ngay lập tức khi logout rõ ràng
            if (userId != null) {
                sessionService.removeSession(userId);
            }

            // TODO: Implement token blacklisting with Redis
            // long ttlMillis = expireAt.getTime() - System.currentTimeMillis();
            // if (ttlMillis > 0) {
            // redisTokenService.blacklistToken(tokenId, Duration.ofMillis(ttlMillis));
            // log.info("Token {} blacklisted, expires in {}ms", tokenId, ttlMillis);
            // }

            log.info("User logged out successfully. Token ID: {}, ExpireAt: {}", tokenId, expireAt);

        } catch (Exception e) {
            log.error("Failed to parse token during logout", e);
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * Generate JWT token with user information
     * 
     * @param userId   User ID to be set as subject
     * @param username Username to be included in claims
     * @param roles    User roles (space-separated string)
     * @return Generated JWT token string
     * @throws JOSEException if token generation fails
     */
    private String generateToken(String userId, String username, String roles,
            long expirationInSeconds, String tokenType, String tokenSecret) throws JOSEException {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("type", tokenType)
                .issuer("Fruvia")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(expirationInSeconds, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString());

        if (roles != null && !roles.isBlank()) {
            claimsBuilder.claim("scope", roles); // Roles for Spring Security
        }

        if (username != null && !username.isBlank()) {
            claimsBuilder.claim("username", username);
        }

        JWTClaimsSet jwtClaimsSet = claimsBuilder.build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(jwsHeader, payload);

        try {
            jwsObject.sign(new MACSigner(tokenSecret.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw e;
        }
    }

    private JWTClaimsSet verifyToken(String token, String tokenSecret, String expectedType)
            throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);

        JWSVerifier verifier = new MACVerifier(tokenSecret.getBytes());
        if (!signedJWT.verify(verifier)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.before(new Date())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        String tokenType = claims.getStringClaim("type");
        if (!expectedType.equals(tokenType)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        return claims;
    }

    @Override
    public boolean checkEmailExists(String email) {
        log.info("Checking if email exists: {}", email);
        return userAuthRepository.existsByEmail(email);
    }

    @Override
    public String generateQrSession() {
        log.info("Generating a new QR login session");
        return qrSessionService.createQrSession();
    }

    @Override
    public void verifyEmailOtp(VerifyOtpRequest request) {
        emailVerificationService.verifyOtp(request.getEmail(), request.getOtp());
    }

    @Override
    public void resendEmailOtp(String email) {
        emailVerificationService.sendVerificationOtp(email);
    }

    @Override
    public void sendPasswordResetOtp(String email) {
        emailVerificationService.sendPasswordResetOtp(email);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        emailVerificationService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
    }

    @Override
    public void qrScanned(String uuid, String userId) throws JOSEException {
        log.info("QR Scanned: {} for user: {}", uuid, userId);

        // Find user details by ID
        var userDetail = userDetailRepository.findById(userId).orElse(null);
        var user = userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Notify Web Client over WebSocket with status "SCANNED" (No data needed, just
        // status)
        AuthenticationResponse response = AuthenticationResponse.builder().build();

        String destination = "/topic/qr-login/" + uuid;
        log.info("Notifying web client (Scanned) via WebSocket on: {}", destination);
        messagingTemplate.convertAndSend(destination, ApiResponse.builder()
                .success(true)
                .message("SCANNED")
                .data(response)
                .build());
    }

    @Override
    public void qrConfirm(String uuid, String userId) throws JOSEException {
        log.info("QR Confirm: {} for user: {}", uuid, userId);

        // Find user by ID
        UserAuth user = userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // Fetch user display name
        var userDetail = userDetailRepository.findById(user.getUserId()).orElse(null);
        String nameToInToken = (userDetail != null && userDetail.getDisplayName() != null)
                ? userDetail.getDisplayName()
                : user.getEmail();

        // Generate token for Web Client with name
        String accessToken = generateToken(
                user.getUserId(),
                nameToInToken,
                DEFAULT_ROLE,
                accessTokenExpiration,
                ACCESS_TOKEN_TYPE,
                accessTokenSecret);

        // Notify Web Client over WebSocket: /topic/qr-login/{uuid}
        AuthenticationResponse response = AuthenticationResponse.builder()
                .accessToken(accessToken)
                .expiresIn(accessTokenExpiration)
                .tokenType("Bearer")
                .build();

        String destination = "/topic/qr-login/" + uuid;
        log.info("Notifying web client via WebSocket on: {}", destination);
        messagingTemplate.convertAndSend(destination, ApiResponse.success(response, "QR đăng nhập thành công!"));
    }
}
