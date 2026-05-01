package iuh.fit.service.user;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.dto.request.user.RegisterRequest;
import iuh.fit.dto.request.user.SetPinRequest;
import iuh.fit.dto.request.user.UpdateAvatarRequest;
import iuh.fit.dto.request.user.UpdateCoverPhotoRequest;
import iuh.fit.dto.request.user.UpdateProfileRequest;
import iuh.fit.dto.response.user.UserMeResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.AccountStatus;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.enums.VerificationType;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.exception.ForbiddenException;
import iuh.fit.mapper.UserMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.repository.UserVerificationRepository;
import iuh.fit.service.conversation.ConversationService;
import iuh.fit.service.search.SearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    private static final String S3_PUBLIC_BASE = "https://fruvia-chat-storage.s3.ap-southeast-1.amazonaws.com/public";

    UserAuthRepository userAuthRepository;
    UserDetailRepository userDetailRepository;
    UserSettingRepository userSettingRepository;
    FriendshipRepository friendshipRepository;
    MessageRepository messageRepository;
    ConversationMemberRepository conversationMemberRepository;
    PasswordEncoder passwordEncoder;
    UserMapper userMapper;
    ConversationService conversationService;
    SearchService searchService;
    UserVerificationRepository userVerificationRepository;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String phoneNumber = request.getPhoneNumber().trim();
        log.info("Registering new user with phone: {}", phoneNumber);

        // Validate phone uniqueness
        if (userAuthRepository.existsByPhoneNumber(phoneNumber)) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // Normalize gmail if provided
        String normalizedGmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : null;
        boolean hasGmail = normalizedGmail != null && !normalizedGmail.isEmpty();

        if (hasGmail && userDetailRepository.findByGmail(normalizedGmail).isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        boolean isEmailVerified = !hasGmail || userVerificationRepository.existsByEmailAndTypeAndIsUsedTrue(
                normalizedGmail,
                VerificationType.REGISTRATION);

        if (hasGmail && !isEmailVerified) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // Generate user ID
        String userId = UUID.randomUUID().toString();

        // Create UserAuth
        String salt = UUID.randomUUID().toString();
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .phoneNumber(phoneNumber)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .salt(salt)
                .accountStatus(AccountStatus.ACTIVE)
                .isTwoFactorEnabled(false)
                .isVerified(isEmailVerified)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        userAuthRepository.save(userAuth);
        log.info("UserAuth created for userId: {}", userId);

        // Assign random default avatar (image1.jpg to image8.jpg)
        int defaultAvatarIndex = (int) (Math.random() * 8) + 1;
        String defaultAvatar = S3_PUBLIC_BASE + "/avatar/image" + defaultAvatarIndex + ".jpg";

        // Assign deterministic default cover photo (image1-3) based on userId char-code
        // hash
        // Matches frontend getDefaultCoverPhoto() logic so the same image is shown
        // before/after save
        int coverIndex = (userId.chars().reduce(0, Integer::sum) % 3) + 1;
        String defaultCoverPhoto = "/background/image" + coverIndex + ".jpg";

        // Create UserDetail
        UserDetail userDetail = UserDetail.builder()
                .userId(userId)
                .displayName(request.getDisplayName())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dob(request.getDob())
                .gender(request.getGender())
                .gmail(normalizedGmail != null ? normalizedGmail : "")
                .avatarUrl(defaultAvatar)
                .coverPhotoUrl(defaultCoverPhoto)
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
                .allowSearchByPhone(true)
                .allowSearchByQR(true)
                .allowSearchByGroup(true)
                .blockStrangerMessages(false)
                .blockStrangerProfileView(false)
                .build();

        userSettingRepository.save(userSetting);
        log.info("UserSetting created for userId: {}", userId);

        initializePostRegistrationResources(userAuth, userDetail);

        // --- CÁCH 1: KHÔNG XÀI MAPPER (Thủ công) ---
        return UserResponse.builder()
                .userId(userId)
                .phoneNumber(userAuth.getPhoneNumber())
                .gmail(normalizedGmail)
                .displayName(userDetail.getDisplayName())
                .firstName(userDetail.getFirstName())
                .lastName(userDetail.getLastName())
                .avatarUrl(userDetail.getAvatarUrl())
                .accountStatus(userAuth.getAccountStatus().name())
                .isVerified(userAuth.getIsVerified())
                .gender(userDetail.getGender())
                .dob(userDetail.getDob())
                .build();
    }

    /**
     * PHẬN BẢN SỬ DỤNG MAPPER (Đã cập nhật cho phone login)
     */
    @Transactional
    public UserResponse registerWithMapper(RegisterRequest request) {
        String phoneNumber = request.getPhoneNumber().trim();
        log.info("Registering (with Mapper) for phone: {}", phoneNumber);

        // 1. Kiểm tra duy nhất
        if (userAuthRepository.existsByPhoneNumber(phoneNumber)) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        String normalizedGmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : null;
        boolean hasGmail = normalizedGmail != null && !normalizedGmail.isEmpty();

        if (hasGmail && userDetailRepository.findByGmail(normalizedGmail).isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        boolean isEmailVerified = !hasGmail || userVerificationRepository.existsByEmailAndTypeAndIsUsedTrue(
                normalizedGmail,
                VerificationType.REGISTRATION);

        if (hasGmail && !isEmailVerified) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 2. Tạo ID chuyên biệt
        String userId = UUID.randomUUID().toString();

        // 3. Xây dựng các Entity
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .phoneNumber(phoneNumber)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .accountStatus(AccountStatus.ACTIVE)
                .isVerified(isEmailVerified)
                .createdAt(LocalDateTime.now())
                .build();

        UserDetail userDetail = UserDetail.builder()
                .userId(userId)
                .displayName(request.getDisplayName())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .gmail(normalizedGmail != null ? normalizedGmail : "")
                .build();

        UserSetting userSetting = UserSetting.builder()
                .userId(userId)
                .allowFriendRequests(true)
                .build();

        // 4. Lưu vào Database
        userAuthRepository.save(userAuth);
        userDetailRepository.save(userDetail);
        userSettingRepository.save(userSetting);

        initializePostRegistrationResources(userAuth, userDetail);

        // --- CÁCH 2: XÀI MAPPER ---
        return userMapper.toUserResponse(userAuth, userDetail, null);
    }

    @Override
    public UserResponse getUserById(String userId, String currentUserId) {
        log.info("Getting user by ID: {} for viewer: {}", userId, currentUserId);

        UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Privacy guards — only when a different user is viewing
        if (currentUserId != null && !currentUserId.equals(userId)) {
            UserSetting targetSetting = userSettingRepository.findById(userId).orElse(null);
            if (targetSetting != null && Boolean.TRUE.equals(targetSetting.getAccountLocked())) {
                throw new ForbiddenException(ErrorCode.USER_ACCOUNT_LOCKED);
            }
            if (targetSetting != null && Boolean.TRUE.equals(targetSetting.getBlockStrangerProfileView())) {
                boolean areFriends = friendshipRepository
                        .findByRequesterIdAndReceiverId(currentUserId, userId)
                        .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                        .isPresent()
                        || friendshipRepository
                                .findByRequesterIdAndReceiverId(userId, currentUserId)
                                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                                .isPresent();
                if (!areFriends) {
                    throw new ForbiddenException(ErrorCode.USER_PROFILE_PRIVATE);
                }
            }
        }

        UserDetail userDetail = userDetailRepository.findByUserId(userId)
                .orElse(null);

        return userMapper.toUserResponse(userAuth, userDetail, currentUserId);
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

            // Backfill coverPhotoUrl for existing users who were created before this
            // feature
            if (userDetail.getCoverPhotoUrl() == null || userDetail.getCoverPhotoUrl().isEmpty()) {
                int coverIdx = (userId.chars().reduce(0, Integer::sum) % 3) + 1;
                userDetail.setCoverPhotoUrl("/background/image" + coverIdx + ".jpg");
                userDetailRepository.save(userDetail);
                log.info("Backfilled coverPhotoUrl for userId: {}", userId);
            }
        }

        return UserMeResponse.builder()
                .id(userId)
                .fullName(fullName)
                .gender(userDetail != null ? userDetail.getGender() : null)
                .dob(userDetail != null ? userDetail.getDob() : null)
                .phoneNumber(userAuth.getPhoneNumber())
                .gmail(userDetail != null ? userDetail.getGmail() : null)
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
            // Clear firstName and lastName to ensure fullName calculation on /me uses
            // displayName
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

        // Re-index user in Elasticsearch after profile update
        UserAuth auth = userAuthRepository.findById(userId).orElse(null);
        searchService.indexUser(userDetail,
                userDetail.getGmail() != null ? userDetail.getGmail() : "",
                auth != null ? auth.getPhoneNumber() : "");

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
    @Transactional
    public UserMeResponse updateCoverPhoto(String userId, UpdateCoverPhotoRequest request) {
        log.info("Updating cover photo for userId: {}", userId);

        UserDetail userDetail = userDetailRepository.findByUserId(userId)
                .orElseGet(() -> UserDetail.builder().userId(userId).build());

        if (request.getCoverPhotoUrl() != null) {
            userDetail.setCoverPhotoUrl(request.getCoverPhotoUrl());
        }

        userDetail.setLastUpdateProfile(LocalDateTime.now());
        userDetailRepository.save(userDetail);

        return getUserMe(userId);
    }

    @Override
    public UserResponse getUserByEmail(String email, String currentUserId) {
        log.info("Getting user by gmail: {} for searcher: {}", email, currentUserId);

        UserDetail userDetail = userDetailRepository.findByGmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserAuth userAuth = userAuthRepository.findById(userDetail.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(userAuth, userDetail, currentUserId);
    }

    @Override
    public UserResponse getUserByPhone(String phoneNumber, String currentUserId) {
        log.info("Getting user by phone: {} for searcher: {}", phoneNumber, currentUserId);

        UserAuth userAuth = userAuthRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserDetail userDetail = userDetailRepository.findByUserId(userAuth.getUserId())
                .orElse(null);

        return userMapper.toUserResponse(userAuth, userDetail, currentUserId);
    }

    // ==================== PIN MANAGEMENT ====================

    @Override
    @Transactional
    public void setupPin(String userId, SetPinRequest request) {
        UserAuth userAuth = userAuthRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean alreadyHasPin = userAuth.getPinCode() != null;

        if (alreadyHasPin) {
            if (request.getCurrentPin() == null || request.getCurrentPin().isBlank()) {
                throw new RuntimeException("Vui lòng nhập mã PIN hiện tại để thay đổi");
            }
            if (!passwordEncoder.matches(request.getCurrentPin(), userAuth.getPinCode())) {
                throw new RuntimeException("Mã PIN hiện tại không đúng");
            }
        }

        userAuth.setPinCode(passwordEncoder.encode(request.getPin()));
        userAuthRepository.save(userAuth);
        log.info("User {} {} PIN", userId, alreadyHasPin ? "changed" : "set");
    }

    @Override
    public boolean hasPinConfigured(String userId) {
        return userAuthRepository.findById(userId)
                .map(u -> u.getPinCode() != null)
                .orElse(false);
    }

    @Override
    public boolean verifyPin(String userId, String rawPin) {
        return userAuthRepository.findById(userId)
                .map(u -> u.getPinCode() != null && passwordEncoder.matches(rawPin, u.getPinCode()))
                .orElse(false);
    }

    @Override
    public java.util.Map<String, Object> getAiTokenUsageToday(String userId) {
        java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        java.util.List<String> conversationIds = conversationMemberRepository.findByUserId(userId)
                .stream()
                .map(iuh.fit.entity.ConversationMember::getConversationId)
                .collect(java.util.stream.Collectors.toList());

        long totalTokens = 0;
        int requestCount = 0;
        if (!conversationIds.isEmpty()) {
            java.util.List<iuh.fit.entity.Message> aiMessages = messageRepository
                    .findAiMessagesByConversationIdsAfter(conversationIds, startOfDay);
            for (iuh.fit.entity.Message msg : aiMessages) {
                if (msg.getTotalTokens() != null)
                    totalTokens += msg.getTotalTokens();
                requestCount++;
            }
        }

        java.util.Map<String, Object> usage = new java.util.HashMap<>();
        usage.put("totalTokensToday", totalTokens);
        usage.put("requestCount", requestCount);
        usage.put("date", java.time.LocalDate.now().toString());
        return usage;
    }

    private void initializePostRegistrationResources(UserAuth userAuth, UserDetail userDetail) {
        try {
            searchService.indexUser(userDetail,
                    userDetail.getGmail() != null ? userDetail.getGmail() : "",
                    userAuth.getPhoneNumber());
        } catch (Exception ex) {
            log.warn("Failed to index user {} after registration: {}", userAuth.getUserId(), ex.getMessage());
        }

        try {
            conversationService.getOrCreateSelfConversation(userAuth.getUserId());
        } catch (Exception ex) {
            log.warn("Failed to create self conversation for user {}: {}", userAuth.getUserId(), ex.getMessage());
        }
    }
}
