package iuh.fit.service.user;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.request.user.UpdateAvatarRequest;
import iuh.fit.dto.request.user.UpdateProfileRequest;
import iuh.fit.dto.response.user.UserMeResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.AccountStatus;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.mapper.UserMapper;
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
    UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Validate email and phone uniqueness
        if (userAuthRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        if (userAuthRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AppException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
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



        // Assign random default avatar (image1.jpg to image8.jpg)
        int defaultAvatarIndex = (int)(Math.random() * 8) + 1;
        String defaultAvatar = "/default/image" + defaultAvatarIndex + ".jpg";

        // Create UserDetail
        UserDetail userDetail = UserDetail.builder()
                .userId(userId)
                .displayName(request.getDisplayName())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dob(request.getDob())
                .gender(request.getGender())
                .avatarUrl(defaultAvatar)
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

        // --- CÁCH 1: KHÔNG XÀI MAPPER (Thủ công) ---
        return UserResponse.builder()
                .userId(userId)
                .phoneNumber(userAuth.getPhoneNumber())
                .email(userAuth.getEmail())
                .displayName(userDetail.getDisplayName())
                .firstName(userDetail.getFirstName())
                .lastName(userDetail.getLastName())
                .avatarUrl(userDetail.getAvatarUrl())
                .accountStatus(userAuth.getAccountStatus().name())
                .gender(userDetail.getGender())
                .dob(userDetail.getDob())
                .build();
    }

    /**
     * PHIÊN BẢN SỬ DỤNG MAPPER (Viết đầy đủ để so sánh)
     */
    @Transactional
    public UserResponse registerWithMapper(RegisterRequest request) {
        log.info("Registering (with Mapper) for email: {}", request.getEmail());

        // 1. Kiểm tra duy nhất
        if (userAuthRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (userAuthRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AppException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
        }

        // 2. Tạo ID chuyên biệt (UUID đã được cấu hình tự động trong Entity, nhưng gán tay để đồng bộ các bảng)
        String userId = UUID.randomUUID().toString();

        // 3. Xây dựng các Entity
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        UserDetail userDetail = UserDetail.builder()
                .userId(userId)
                .displayName(request.getDisplayName())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        UserSetting userSetting = UserSetting.builder()
                .userId(userId)
                .allowFriendRequests(true)
                .build();

        // 4. Lưu vào Database
        userAuthRepository.save(userAuth);
        userDetailRepository.save(userDetail);
        userSettingRepository.save(userSetting);

        // --- CÁCH 2: XÀI MAPPER ---
        return userMapper.toUserResponse(userAuth, userDetail, null);
    }

    @Override
    public UserResponse getUserById(String userId) {
        log.info("Getting user by ID: {}", userId);

        UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

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
                .gender(userDetail != null ? userDetail.getGender() : null)
                .dob(userDetail != null ? userDetail.getDob() : null)
                .build();
    }

    @Override
    public UserMeResponse getUserMe(String userId) {
        log.info("Getting user me information for userId: {}", userId);

        UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserDetail userDetail = userDetailRepository.findByUserId(userId)
                .orElse(null);

        String fullName = "";
        if (userDetail != null) {
            String firstName = userDetail.getFirstName() != null ? userDetail.getFirstName() : "";
            String lastName = userDetail.getLastName() != null ? userDetail.getLastName() : "";
            fullName = (lastName + " " + firstName).trim();
            if (fullName.isEmpty()) {
                fullName = userDetail.getDisplayName();
            }
        }

        return UserMeResponse.builder()
                .fullName(fullName)
                .gender(userDetail != null ? userDetail.getGender() : null)
                .dob(userDetail != null ? userDetail.getDob() : null)
                .phoneNumber(userAuth.getPhoneNumber())
                .bio(userDetail != null ? userDetail.getBio() : null)
                .address(userDetail != null ? userDetail.getAddress() : null)
                .city(userDetail != null ? userDetail.getCity() : null)
                .education(userDetail != null ? userDetail.getEducation() : null)
                .workplace(userDetail != null ? userDetail.getWorkplace() : null)
                .avatarUrl(userDetail != null ? userDetail.getAvatarUrl() : null)
                .coverPhotoUrl(userDetail != null ? userDetail.getCoverPhotoUrl() : null)
                .build();
    }

    @Override
    @Transactional
    public UserMeResponse updateProfile(String userId, UpdateProfileRequest request) {
        log.info("Updating profile for userId: {}", userId);

        userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserDetail userDetail = userDetailRepository.findByUserId(userId)
                .orElseGet(() -> UserDetail.builder().userId(userId).build());

        if (request.getFullName() != null) {
            userDetail.setDisplayName(request.getFullName());
            // Clear firstName and lastName to ensure fullName calculation on /me uses displayName
            userDetail.setFirstName(null);
            userDetail.setLastName(null);
        }

        if (request.getGender() != null) {
            userDetail.setGender(request.getGender());
        }

        if (request.getDob() != null) {
            userDetail.setDob(request.getDob());
        }

        if (request.getBio() != null) {
            userDetail.setBio(request.getBio());
        }

        if (request.getAddress() != null) {
            userDetail.setAddress(request.getAddress());
        }

        if (request.getCity() != null) {
            userDetail.setCity(request.getCity());
        }

        if (request.getEducation() != null) {
            userDetail.setEducation(request.getEducation());
        }

        if (request.getWorkplace() != null) {
            userDetail.setWorkplace(request.getWorkplace());
        }

        if (request.getAvatarUrl() != null) {
            userDetail.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getCoverPhotoUrl() != null) {
            userDetail.setCoverPhotoUrl(request.getCoverPhotoUrl());
        }

        userDetail.setLastUpdateProfile(LocalDateTime.now());
        userDetailRepository.save(userDetail);

        return getUserMe(userId);
    }

    @Override
    @Transactional
    public UserMeResponse updateAvatar(String userId, UpdateAvatarRequest request) {
        log.info("Updating avatar for userId: {}", userId);

        UserDetail userDetail = userDetailRepository.findByUserId(userId)
                .orElseGet(() -> UserDetail.builder().userId(userId).build());

        if (request.getAvatarUrl() != null) {
            userDetail.setAvatarUrl(request.getAvatarUrl());
        }

        userDetail.setLastUpdateProfile(LocalDateTime.now());
        userDetailRepository.save(userDetail);

        return getUserMe(userId);
    }

    @Override
    public UserResponse getUserByPhoneNumber(String phoneNumber, String currentUserId) {
        log.info("Getting user by phone number: {} for searcher: {}", phoneNumber, currentUserId);

        UserAuth userAuth = userAuthRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserDetail userDetail = userDetailRepository.findByUserId(userAuth.getUserId())
                .orElse(null);

        return userMapper.toUserResponse(userAuth, userDetail, currentUserId);
    }
}
