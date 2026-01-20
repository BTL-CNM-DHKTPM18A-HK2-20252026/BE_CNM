package iuh.fit.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
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
    String userId; // Primary key and reference to other user collections
    
    String phoneNumber;
    String email;
    String passwordHash;
    String salt;
    
    AccountStatus accountStatus;
    Boolean isTwoFactorEnabled;
    
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime lastLoginAt;
    Boolean isDeleted;
}
