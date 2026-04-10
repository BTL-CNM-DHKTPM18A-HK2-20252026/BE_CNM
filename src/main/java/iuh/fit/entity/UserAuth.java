package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.AccountStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * UserAuth entity - Stores authentication information
 * Related to UserDetail, UserSetting, UserVerification, UserDevice
 */
@Document(collection = "user_auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAuth {

    @Id
    @Builder.Default
    String userId = UUID.randomUUID().toString(); // Primary key and reference to other user collections

    @Indexed(unique = true, sparse = true)
    String phoneNumber; // Primary login identifier

    String passwordHash;
    String salt;

    AccountStatus accountStatus;
    Boolean isTwoFactorEnabled;

    @Builder.Default
    Boolean isVerified = true;

    /**
     * Bcrypt-hashed 6-digit PIN used to protect hidden conversations. Null if not
     * set.
     */
    String pinCode;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime lastLoginAt;
    Boolean isDeleted;
}
