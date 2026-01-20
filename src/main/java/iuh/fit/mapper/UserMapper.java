package iuh.fit.mapper;

import org.springframework.stereotype.Component;

import iuh.fit.dto.response.user.UserProfileResponse;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.FriendShipRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserMapper {
    
    private final FriendShipRepository friendShipRepository;
    
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
        long friendCount = friendShipRepository.countByUserId1OrUserId2(userAuth.getUserId(), userAuth.getUserId());
        builder.friendCount((int) friendCount);
        
        // Check if current user is friend
        if (currentUserId != null && !currentUserId.equals(userAuth.getUserId())) {
            boolean isFriend = friendShipRepository.findByUserId1AndUserId2(currentUserId, userAuth.getUserId()).isPresent() ||
                    friendShipRepository.findByUserId1AndUserId2(userAuth.getUserId(), currentUserId).isPresent();
            builder.isFriend(isFriend);
        }
        
        return builder.build();
    }
}
