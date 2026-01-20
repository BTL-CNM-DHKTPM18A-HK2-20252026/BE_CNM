package iuh.fit.service.user;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.AccountStatus;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.UserSettingRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserAuthRepository userAuthRepository;
    UserDetailRepository userDetailRepository;
    UserSettingRepository userSettingRepository;
    PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Validate email and phone uniqueness
        if (userAuthRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        if (userAuthRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new RuntimeException("Phone number already exists");
        }

        // Generate user ID
        String userId = UUID.randomUUID().toString();

        // Create UserAuth
        String salt = UUID.randomUUID().toString();
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .salt(salt)
                .accountStatus(AccountStatus.ACTIVE)
                .isTwoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        userAuthRepository.save(userAuth);
        log.info("UserAuth created for userId: {}", userId);

        // Create UserDetail
        UserDetail userDetail = UserDetail.builder()
                .userId(userId)
                .displayName(request.getDisplayName())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isOrgActive(false)
                .lastUpdateProfile(LocalDateTime.now())
                .build();

        userDetailRepository.save(userDetail);
        log.info("UserDetail created for userId: {}", userId);

        // Create UserSetting with default values
        UserSetting userSetting = UserSetting.builder()
                .userId(userId)
                .allowFriendRequests(true)
                .whoCanSeeProfile(PrivacyLevel.PUBLIC)
                .whoCanSeePost(PrivacyLevel.FRIEND_ONLY)
                .whoCanTagMe(PrivacyLevel.FRIEND_ONLY)
                .whoCanSendMessages(PrivacyLevel.PUBLIC)
                .showOnlineStatus(true)
                .showReadReceipts(true)
                .build();

        userSettingRepository.save(userSetting);
        log.info("UserSetting created for userId: {}", userId);

        // Return user response
        return UserResponse.builder()
                .userId(userId)
                .phoneNumber(userAuth.getPhoneNumber())
                .email(userAuth.getEmail())
                .displayName(userDetail.getDisplayName())
                .firstName(userDetail.getFirstName())
                .lastName(userDetail.getLastName())
                .avatarUrl(userDetail.getAvatarUrl())
                .accountStatus(userAuth.getAccountStatus().name())
                .build();
    }

    @Override
    public UserResponse getUserById(String userId) {
        log.info("Getting user by ID: {}", userId);

        UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDetail userDetail = userDetailRepository.findByUserId(userId)
                .orElse(null);

        return UserResponse.builder()
                .userId(userAuth.getUserId())
                .phoneNumber(userAuth.getPhoneNumber())
                .email(userAuth.getEmail())
                .displayName(userDetail != null ? userDetail.getDisplayName() : null)
                .firstName(userDetail != null ? userDetail.getFirstName() : null)
                .lastName(userDetail != null ? userDetail.getLastName() : null)
                .avatarUrl(userDetail != null ? userDetail.getAvatarUrl() : null)
                .accountStatus(userAuth.getAccountStatus().name())
                .build();
    }
}
