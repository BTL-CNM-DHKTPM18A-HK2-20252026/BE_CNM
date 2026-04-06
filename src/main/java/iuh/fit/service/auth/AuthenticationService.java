package iuh.fit.service.auth;

import com.nimbusds.jose.JOSEException;
import iuh.fit.dto.request.auth.AuthenticationRequest;
import iuh.fit.dto.request.auth.IntrospectRequest;
import iuh.fit.dto.request.auth.LogoutRequest;
import iuh.fit.dto.request.auth.ResetPasswordRequest;
import iuh.fit.dto.request.auth.VerifyOtpRequest;
import iuh.fit.dto.response.auth.AuthenticationResponse;
import iuh.fit.dto.response.auth.IntrospectResponse;

import java.text.ParseException;

public interface AuthenticationService {

    /**
     * Authenticate user with username/email and password
     * 
     * @param request Authentication request containing username and password
     * @return Authentication response with access token
     * @throws JOSEException if JWT creation fails
     */
    AuthenticationResponse authenticate(AuthenticationRequest request) throws JOSEException;

    /**
     * Create a new access token from a valid refresh token.
     *
     * @param refreshToken refresh token from HttpOnly cookie
     * @return authentication response containing a new access token
     * @throws JOSEException  if JWT verification/signing fails
     * @throws ParseException if JWT parsing fails
     */
    AuthenticationResponse refreshAccessToken(String refreshToken) throws JOSEException, ParseException;

    /**
     * Introspect token to check if it's valid
     * 
     * @param request Introspect request containing token
     * @return Introspect response with token validity
     * @throws JOSEException  if JWT verification fails
     * @throws ParseException if JWT parsing fails
     */
    IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException;

    /**
     * Logout user and blacklist the token
     * 
     * @param request Logout request containing token
     */
    void logout(LogoutRequest request);

    /**
     * Check if a phone number exists in the database
     * 
     * @param phoneNumber Phone number to check
     * @return true if exists, false otherwise
     */
    boolean checkPhoneNumberExists(String phoneNumber);

    /**
     * Generate a new QR session UUID and store it in Redis with a short TTL (120s)
     * 
     * @return Generated UUID string
     */
    String generateQrSession();

    /**
     * Verify email by matching OTP code.
     *
     * @param request email and otp payload
     */
    void verifyEmailOtp(VerifyOtpRequest request);

    /**
     * Resend email verification OTP.
     *
     * @param email target email
     */
    void resendEmailOtp(String email);

    /**
     * Send OTP for forgot-password flow.
     *
     * @param email target email
     */
    void sendPasswordResetOtp(String email);

    /**
     * Reset account password using email + OTP.
     *
     * @param request reset payload
     */
    void resetPassword(ResetPasswordRequest request);

    void qrScanned(String uuid, String userId) throws JOSEException;

    /**
     * Confirm a QR login from a mobile device
     * 
     * @param uuid   QR session UUID to confirm
     * @param userId User ID of the logged-in mobile user
     * @throws JOSEException if token generation fails
     */
    void qrConfirm(String uuid, String userId) throws JOSEException;
}
