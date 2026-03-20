package iuh.fit.service.friend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.entity.FriendRequest;
import iuh.fit.entity.FriendShip;
import iuh.fit.enums.RequestStatus;
import iuh.fit.mapper.FriendMapper;
import iuh.fit.repository.FriendRequestRepository;
import iuh.fit.repository.FriendShipRepository;
import iuh.fit.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendService {
    
    private final FriendRequestRepository friendRequestRepository;
    private final FriendShipRepository friendShipRepository;
    private final ConversationService conversationService;
    private final FriendMapper friendMapper;
    
    @Transactional
    public FriendRequestResponse sendFriendRequest(String senderId, String receiverId) {
        // Check if already friends
        if (friendShipRepository.findByUserId1AndUserId2(senderId, receiverId).isPresent()) {
            throw new RuntimeException("Already friends");
        }
        
        // Check if request already exists
        if (friendRequestRepository.findBySenderIdAndReceiverId(senderId, receiverId).isPresent()) {
            throw new RuntimeException("Friend request already sent");
        }
        
        FriendRequest request = FriendRequest.builder()
                .senderId(senderId)
                .receiverId(receiverId)
            .status(RequestStatus.PENDING)
            .sentAt(LocalDateTime.now())
                .build();
        
        request = friendRequestRepository.save(request);
        log.info("Friend request sent from {} to {}", senderId, receiverId);
        
        return friendMapper.toResponse(request);
    }
    
    @Transactional
    public void acceptFriendRequest(String requestId, String userId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));
        
        if (!request.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized to accept this request");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }
        
        // Update request status
        request.setStatus(RequestStatus.ACCEPTED);
        request.setResponseAt(LocalDateTime.now());
        friendRequestRepository.save(request);
        
        // Create friendship
        FriendShip friendship = FriendShip.builder()
            .userId1(request.getSenderId())
            .userId2(request.getReceiverId())
            .createdAt(LocalDateTime.now())
                .build();
        
        friendShipRepository.save(friendship);
        
        // TỰ ĐỘNG tạo cuộc hội thoại 1-1 ngay khi kết bạn (như Zalo/Facebook)
        conversationService.getOrCreatePrivateConversation(request.getSenderId(), request.getReceiverId());
        
        log.info("Friend request accepted and conversation created: {}", requestId);
    }
    
    @Transactional
    public void rejectFriendRequest(String requestId, String userId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));
        
        if (!request.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized to reject this request");
        }
        
        request.setStatus(RequestStatus.REJECTED);
        request.setResponseAt(LocalDateTime.now());
        friendRequestRepository.save(request);
        log.info("Friend request rejected: {}", requestId);
    }
    
    public List<FriendRequestResponse> getPendingRequests(String userId) {
        return friendRequestRepository.findByReceiverIdAndStatus(userId, RequestStatus.PENDING)
                .stream()
                .map(friendMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void unfriend(String userId1, String userId2) {
        friendShipRepository.deleteByUserId1AndUserId2(userId1, userId2);
        friendShipRepository.deleteByUserId1AndUserId2(userId2, userId1);
        log.info("Unfriended: {} and {}", userId1, userId2);
    }
}
