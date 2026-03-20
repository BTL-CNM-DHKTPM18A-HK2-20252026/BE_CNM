package iuh.fit.mapper;

import org.springframework.stereotype.Component;

import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.entity.FriendRequest;

@Component
public class FriendMapper {
    
    public FriendRequestResponse toResponse(FriendRequest request) {
        if (request == null) {
            return null;
        }
        
        return FriendRequestResponse.builder()
                .requestId(request.getRequestId())
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .status(request.getStatus() != null ? request.getStatus().toString() : null)
                .createdAt(request.getSentAt())
                .build();
    }
}
