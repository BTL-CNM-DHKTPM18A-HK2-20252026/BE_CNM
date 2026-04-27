package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.AccountStatus;

/**
 * UserAuth entity - Stores authentication information
 * Related to UserDetail, UserSetting, UserVerification, UserDevice
 */
@Document(collection = "user_auth")
public class UserAuth {

    @Id
    private String userId = UUID.randomUUID().toString();

    @Indexed(unique = true, sparse = true)
    private String phoneNumber;

    private String passwordHash;
    private String salt;
    private AccountStatus accountStatus;
    private Boolean isTwoFactorEnabled;
    private Boolean isVerified = true;
    private String pinCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private Boolean isDeleted;

    public UserAuth() {}

    public UserAuth(String userId, String phoneNumber, String passwordHash, String salt, AccountStatus accountStatus, 
                   Boolean isTwoFactorEnabled, Boolean isVerified, String pinCode, LocalDateTime createdAt, 
                   LocalDateTime updatedAt, LocalDateTime lastLoginAt, Boolean isDeleted) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.accountStatus = accountStatus;
        this.isTwoFactorEnabled = isTwoFactorEnabled;
        this.isVerified = isVerified;
        this.pinCode = pinCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
        this.isDeleted = isDeleted;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public Boolean getIsTwoFactorEnabled() { return isTwoFactorEnabled; }
    public void setIsTwoFactorEnabled(Boolean isTwoFactorEnabled) { this.isTwoFactorEnabled = isTwoFactorEnabled; }
    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public static UserAuthBuilder builder() {
        return new UserAuthBuilder();
    }

    public static class UserAuthBuilder {
        private String userId = UUID.randomUUID().toString();
        private String phoneNumber;
        private String passwordHash;
        private String salt;
        private AccountStatus accountStatus;
        private Boolean isTwoFactorEnabled;
        private Boolean isVerified = true;
        private String pinCode;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime lastLoginAt;
        private Boolean isDeleted;

        public UserAuthBuilder userId(String userId) { this.userId = userId; return this; }
        public UserAuthBuilder phoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; return this; }
        public UserAuthBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public UserAuthBuilder salt(String salt) { this.salt = salt; return this; }
        public UserAuthBuilder accountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; return this; }
        public UserAuthBuilder isTwoFactorEnabled(Boolean isTwoFactorEnabled) { this.isTwoFactorEnabled = isTwoFactorEnabled; return this; }
        public UserAuthBuilder isVerified(Boolean isVerified) { this.isVerified = isVerified; return this; }
        public UserAuthBuilder pinCode(String pinCode) { this.pinCode = pinCode; return this; }
        public UserAuthBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public UserAuthBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public UserAuthBuilder lastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; return this; }
        public UserAuthBuilder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }

        public UserAuth build() {
            return new UserAuth(userId, phoneNumber, passwordHash, salt, accountStatus, isTwoFactorEnabled, isVerified, pinCode, createdAt, updatedAt, lastLoginAt, isDeleted);
        }
    }
}
