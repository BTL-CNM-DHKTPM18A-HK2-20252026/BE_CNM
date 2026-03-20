package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.VerificationType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * UserVerification entity - Stores verification codes and status
 * Related to UserAuth (userId)
 */
@Document(collection = "user_verification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserVerification {
    
    @Id
    @Builder.Default
    String verificationId = UUID.randomUUID().toString();
    
    String userId; // Reference to UserAuth
    String otpCode;
    VerificationType type;
    LocalDateTime expiresAt;
    Boolean isUsed;
    LocalDateTime createdAt;
}
