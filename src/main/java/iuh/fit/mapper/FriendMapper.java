package iuh.fit.mapper;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.entity.Friendship;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;

@Component
@RequiredArgsConstructor
public class FriendMapper {

    private final UserAuthRepository userAuthRepository;
    private final UserDetailRepository userDetailRepository;

    public FriendRequestResponse toResponse(Friendship rel) {
        if (rel == null) {
            return null;
        }

        FriendRequestResponse.FriendRequestResponseBuilder builder = FriendRequestResponse.builder()
                .requestId(rel.getId())
                .senderId(rel.getRequesterId())
                .receiverId(rel.getReceiverId())
                .status(rel.getStatus() != null ? rel.getStatus().toString() : null)
                .message(rel.getMessage())
                .createdAt(rel.getCreatedAt());

        // Fetch Sender Info
        userAuthRepository.findById(rel.getRequesterId()).ifPresent(auth -> {
            builder.senderName(auth.getPhoneNumber()); // Fallback
            userDetailRepository.findByUserId(auth.getUserId()).ifPresent(detail -> {
                builder.senderName(detail.getDisplayName());
                builder.senderAvatarUrl(detail.getAvatarUrl());
            });
        });

        // Fetch Receiver Info
        userAuthRepository.findById(rel.getReceiverId()).ifPresent(auth -> {
            builder.receiverName(auth.getPhoneNumber()); // Fallback
            userDetailRepository.findByUserId(auth.getUserId()).ifPresent(detail -> {
                builder.receiverName(detail.getDisplayName());
                builder.receiverAvatarUrl(detail.getAvatarUrl());
            });
        });

        return builder.build();
    }
}
