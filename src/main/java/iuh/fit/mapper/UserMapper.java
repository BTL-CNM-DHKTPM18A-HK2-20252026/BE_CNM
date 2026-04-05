package iuh.fit.mapper;

import org.springframework.stereotype.Component;

import iuh.fit.dto.response.user.UserProfileResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final FriendshipRepository friendshipRepository;

    public UserProfileResponse toProfileResponse(UserAuth userAuth, UserDetail userDetail, String currentUserId) {
        if (userAuth == null) {
            return null;
        }

        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .userId(userAuth.getUserId())
                .email(userAuth.getEmail())
                .phoneNumber(userAuth.getPhoneNumber())
                .createdAt(userAuth.getCreatedAt());

        if (userDetail != null) {
            builder.displayName(userDetail.getDisplayName())
                    .firstName(userDetail.getFirstName())
                    .lastName(userDetail.getLastName())
                    .avatarUrl(userDetail.getAvatarUrl())
                    .coverPhotoUrl(userDetail.getCoverPhotoUrl())
                    .bio(userDetail.getBio());
        }

        // Calculate friend count
        long friendCount = friendshipRepository.countAcceptedFriends(userAuth.getUserId());
        builder.friendCount((int) friendCount);

        // Check if current user is friend
        if (currentUserId != null && !currentUserId.equals(userAuth.getUserId())) {
            boolean isFriend = friendshipRepository
                    .findByRequesterIdAndReceiverIdAndStatus(currentUserId, userAuth.getUserId(),
                            FriendshipStatus.ACCEPTED)
                    .isPresent() ||
                    friendshipRepository.findByRequesterIdAndReceiverIdAndStatus(userAuth.getUserId(), currentUserId,
                            FriendshipStatus.ACCEPTED).isPresent();
            builder.isFriend(isFriend);
        }

        return builder.build();
    }

    public UserResponse toUserResponse(UserAuth userAuth, UserDetail userDetail, String currentUserId) {
        if (userAuth == null)
            return null;

        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .userId(userAuth.getUserId())
                .phoneNumber(userAuth.getPhoneNumber())
                .email(userAuth.getEmail())
                .isVerified(userAuth.getIsVerified())
                .accountStatus(userAuth.getAccountStatus() != null ? userAuth.getAccountStatus().name() : null);

        if (userDetail != null) {
            builder.displayName(userDetail.getDisplayName())
                    .firstName(userDetail.getFirstName())
                    .lastName(userDetail.getLastName())
                    .avatarUrl(userDetail.getAvatarUrl());
        }

        // Check friendship status
        if (currentUserId != null && !currentUserId.equals(userAuth.getUserId())) {
            friendshipRepository.findByRequesterIdAndReceiverId(currentUserId, userAuth.getUserId())
                    .ifPresent(f -> {
                        builder.friendshipStatus(f.getStatus().name());
                        builder.isRequester(f.getRequesterId().equals(currentUserId));
                    });
        }

        return builder.build();
    }
}
