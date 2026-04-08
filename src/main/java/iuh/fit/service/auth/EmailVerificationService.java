package iuh.fit.service.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserVerification;
import iuh.fit.enums.VerificationType;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserVerificationRepository;
import jakarta.mail.MessagingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailVerificationService {

  static final DateTimeFormatter OTP_EXPIRE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");

  final UserAuthRepository userAuthRepository;
  final UserVerificationRepository userVerificationRepository;
  final JavaMailSender mailSender;
  final PasswordEncoder passwordEncoder;

  @Value("${app.email.from:}")
  String mailFrom;

  @Value("${app.otp.ttl-minutes:5}")
  int otpTtlMinutes;

  public void sendVerificationOtp(String email) {
    UserAuth user = findUserByEmail(email);

    if (Boolean.TRUE.equals(user.getIsVerified())) {
      throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED);
    }

    List<UserVerification> activeOtps = userVerificationRepository.findByEmailAndTypeAndIsUsedFalse(
        user.getEmail(), VerificationType.EMAIL);

    String rawOtp = generateSixDigitOtp();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiresAt = now.plusMinutes(otpTtlMinutes);

    UserVerification verification = UserVerification.builder()
        .userId(user.getUserId())
        .email(user.getEmail())
        .otpCode(passwordEncoder.encode(rawOtp))
        .type(VerificationType.EMAIL)
        .isUsed(false)
        .createdAt(now)
        .expiresAt(expiresAt)
        .build();

    userVerificationRepository.save(verification);

    try {
      sendOtpEmail(user.getEmail(), rawOtp, expiresAt);
    } catch (RuntimeException ex) {
      userVerificationRepository.deleteById(verification.getVerificationId());
      throw ex;
    }

    if (!activeOtps.isEmpty()) {
      activeOtps.forEach(otp -> otp.setIsUsed(true));
      userVerificationRepository.saveAll(activeOtps);
    }
  }

  public void sendPasswordResetOtp(String email) {
    UserAuth user = findUserByEmail(email);

    List<UserVerification> activeOtps = userVerificationRepository.findByEmailAndTypeAndIsUsedFalse(
        user.getEmail(), VerificationType.PASSWORD_RESET);

    String rawOtp = generateSixDigitOtp();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiresAt = now.plusMinutes(otpTtlMinutes);

    UserVerification verification = UserVerification.builder()
        .userId(user.getUserId())
        .email(user.getEmail())
        .otpCode(passwordEncoder.encode(rawOtp))
        .type(VerificationType.PASSWORD_RESET)
        .isUsed(false)
        .createdAt(now)
        .expiresAt(expiresAt)
        .build();

    userVerificationRepository.save(verification);

    try {
      sendPasswordResetOtpEmail(user.getEmail(), rawOtp, expiresAt);
    } catch (RuntimeException ex) {
      userVerificationRepository.deleteById(verification.getVerificationId());
      throw ex;
    }

    if (!activeOtps.isEmpty()) {
      activeOtps.forEach(otp -> otp.setIsUsed(true));
      userVerificationRepository.saveAll(activeOtps);
    }
  }

  public void verifyOtp(String email, String otp) {
    UserAuth user = findUserByEmail(email);

    if (Boolean.TRUE.equals(user.getIsVerified())) {
      throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED);
    }

    UserVerification verification = userVerificationRepository
        .findTopByEmailAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user.getEmail(), VerificationType.EMAIL)
        .orElseThrow(() -> new AppException(ErrorCode.OTP_NOT_FOUND));

    if (verification.getExpiresAt() == null || LocalDateTime.now().isAfter(verification.getExpiresAt())) {
      throw new AppException(ErrorCode.OTP_EXPIRED);
    }

    if (!passwordEncoder.matches(otp, verification.getOtpCode())) {
      throw new AppException(ErrorCode.INVALID_OTP);
    }

    verification.setIsUsed(true);
    userVerificationRepository.save(verification);

    user.setIsVerified(true);
    user.setUpdatedAt(LocalDateTime.now());
    userAuthRepository.save(user);
  }

  public void resetPassword(String email, String otp, String newPassword) {
    UserAuth user = findUserByEmail(email);

    UserVerification verification = userVerificationRepository
        .findTopByEmailAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user.getEmail(), VerificationType.PASSWORD_RESET)
        .orElseThrow(() -> new AppException(ErrorCode.OTP_NOT_FOUND));

    if (verification.getExpiresAt() == null || LocalDateTime.now().isAfter(verification.getExpiresAt())) {
      throw new AppException(ErrorCode.OTP_EXPIRED);
    }

    if (!passwordEncoder.matches(otp, verification.getOtpCode())) {
      throw new AppException(ErrorCode.INVALID_OTP);
    }

    verification.setIsUsed(true);
    userVerificationRepository.save(verification);

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setUpdatedAt(LocalDateTime.now());
    userAuthRepository.save(user);
  }

  private UserAuth findUserByEmail(String email) {
    String normalizedEmail = email == null ? "" : email.trim();
    return userAuthRepository.findByEmail(normalizedEmail)
        .or(() -> userAuthRepository.findByEmail(normalizedEmail.toLowerCase(Locale.ROOT)))
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
  }

  private String generateSixDigitOtp() {
    int value = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
    return Integer.toString(value);
  }

  private void sendOtpEmail(String toEmail, String otp, LocalDateTime expiresAt) {
    String subject = "[Fruvia] Mã xác thực email của bạn";
    String htmlBody = buildOtpEmailTemplate(otp, expiresAt);

    try {
      var mimeMessage = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlBody, true);

      if (mailFrom != null && !mailFrom.isBlank()) {
        helper.setFrom(mailFrom);
      }

      mailSender.send(mimeMessage);
    } catch (MessagingException | MailException ex) {
      log.error("Failed to send verification OTP email to {}", toEmail, ex);
      throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể gửi email OTP vào lúc này. Vui lòng thử lại sau.");
    }
  }

  private void sendPasswordResetOtpEmail(String toEmail, String otp, LocalDateTime expiresAt) {
    String subject = "[Fruvia] Mã OTP đặt lại mật khẩu";
    String htmlBody = buildPasswordResetOtpEmailTemplate(otp, expiresAt);

    try {
      var mimeMessage = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlBody, true);

      if (mailFrom != null && !mailFrom.isBlank()) {
        helper.setFrom(mailFrom);
      }

      mailSender.send(mimeMessage);
    } catch (MessagingException | MailException ex) {
      log.error("Failed to send password-reset OTP email to {}", toEmail, ex);
      throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể gửi email đặt lại mật khẩu vào lúc này. Vui lòng thử lại sau.");
    }
  }

  private String buildOtpEmailTemplate(String otp, LocalDateTime expiresAt) {
    String expireText = expiresAt.format(OTP_EXPIRE_FORMATTER);

    return """
        <!doctype html>
        <html lang=\"vi\">
        <head>
          <meta charset=\"UTF-8\" />
          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
          <title>Xác thực Email Fruvia</title>
        </head>
        <body style=\"margin:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#0f172a;\">
          <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"padding:24px 12px;\">
            <tr>
              <td align=\"center\">
                <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #dbe7ff;\">
                  <tr>
                    <td style=\"padding:24px 28px;background:linear-gradient(135deg,#0056d6,#00a8ff);color:#ffffff;\">
                      <h1 style=\"margin:0;font-size:20px;line-height:1.4;\">Xác thực email tài khoản Fruvia</h1>
                      <p style=\"margin:8px 0 0;font-size:13px;opacity:0.9;\">Bảo mật tài khoản của bạn với mã OTP một lần.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style=\"padding:28px;\">
                      <p style=\"margin:0 0 14px;font-size:14px;line-height:1.6;\">Chào bạn,</p>
                      <p style=\"margin:0 0 22px;font-size:14px;line-height:1.6;\">Vui lòng sử dụng mã OTP dưới đây để hoàn tất xác thực email. Mã này có hiệu lực trong <strong>5 phút</strong>.</p>
                      <div style=\"margin:0 0 20px;padding:16px 18px;border:1px dashed #9cc3ff;border-radius:14px;background:#f7fbff;text-align:center;\">
                        <span style=\"display:inline-block;font-size:34px;letter-spacing:10px;font-weight:700;color:#0056d6;\">{{OTP}}</span>
                      </div>
                      <p style=\"margin:0 0 10px;font-size:13px;color:#334155;\">Hết hạn lúc: <strong>{{EXPIRE_AT}}</strong></p>
                      <p style=\"margin:0;font-size:12px;color:#64748b;line-height:1.6;\">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này để bảo vệ tài khoản.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style=\"padding:16px 28px 22px;border-top:1px solid #eef4ff;background:#fbfdff;\">
                      <p style=\"margin:0;font-size:12px;color:#64748b;\">Fruvia Team</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """
        .replace("{{OTP}}", otp)
        .replace("{{EXPIRE_AT}}", expireText);
  }

  private String buildPasswordResetOtpEmailTemplate(String otp, LocalDateTime expiresAt) {
    String expireText = expiresAt.format(OTP_EXPIRE_FORMATTER);

    return """
        <!doctype html>
        <html lang=\"vi\">
        <head>
          <meta charset=\"UTF-8\" />
          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
          <title>Đặt lại mật khẩu Fruvia</title>
        </head>
        <body style=\"margin:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#0f172a;\">
          <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"padding:24px 12px;\">
            <tr>
              <td align=\"center\">
                <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #dbe7ff;\">
                  <tr>
                    <td style=\"padding:24px 28px;background:linear-gradient(135deg,#0056d6,#00a8ff);color:#ffffff;\">
                      <h1 style=\"margin:0;font-size:20px;line-height:1.4;\">Đặt lại mật khẩu tài khoản Fruvia</h1>
                      <p style=\"margin:8px 0 0;font-size:13px;opacity:0.9;\">Sử dụng mã OTP để xác nhận yêu cầu đổi mật khẩu.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style=\"padding:28px;\">
                      <p style=\"margin:0 0 14px;font-size:14px;line-height:1.6;\">Chào bạn,</p>
                      <p style=\"margin:0 0 22px;font-size:14px;line-height:1.6;\">Vui lòng nhập mã OTP dưới đây để đặt lại mật khẩu. Mã này có hiệu lực trong <strong>5 phút</strong>.</p>
                      <div style=\"margin:0 0 20px;padding:16px 18px;border:1px dashed #9cc3ff;border-radius:14px;background:#f7fbff;text-align:center;\">
                        <span style=\"display:inline-block;font-size:34px;letter-spacing:10px;font-weight:700;color:#0056d6;\">{{OTP}}</span>
                      </div>
                      <p style=\"margin:0 0 10px;font-size:13px;color:#334155;\">Hết hạn lúc: <strong>{{EXPIRE_AT}}</strong></p>
                      <p style=\"margin:0;font-size:12px;color:#64748b;line-height:1.6;\">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này để bảo vệ tài khoản.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style=\"padding:16px 28px 22px;border-top:1px solid #eef4ff;background:#fbfdff;\">
                      <p style=\"margin:0;font-size:12px;color:#64748b;\">Fruvia Team</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """
        .replace("{{OTP}}", otp)
        .replace("{{EXPIRE_AT}}", expireText);
  }
}
