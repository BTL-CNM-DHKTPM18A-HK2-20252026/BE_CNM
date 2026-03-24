package iuh.fit.service.friend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.mapper.FriendMapper;
import iuh.fit.mapper.UserMapper;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendService {
    
    private final FriendshipRepository friendshipRepository;
    private final ConversationService conversationService;
    private final FriendMapper friendMapper;
    private final UserAuthRepository userAuthRepository;
    private final UserDetailRepository userDetailRepository;
    private final UserMapper userMapper;
    
    @Transactional
    public FriendRequestResponse sendFriendRequest(String senderId, String receiverId) {
        // 1. Check if receiver has BLOCKED sender
        Optional<Friendship> blockCheck = friendshipRepository.findByRequesterIdAndReceiverIdAndStatus(receiverId, senderId, FriendshipStatus.BLOCKED);
        if (blockCheck.isPresent()) {
            throw new RuntimeException("User not found or you are blocked");
        }

        // 2. Check existing relationship
        Optional<Friendship> existingRel = friendshipRepository.findByRequesterIdAndReceiverId(senderId, receiverId);

        Friendship friendship;
        if (existingRel.isPresent()) {
            friendship = existingRel.get();
            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new RuntimeException("Already friends");
            }
            if (friendship.getStatus() == FriendshipStatus.PENDING) {
                if (friendship.getRequesterId().equals(senderId)) {
                    throw new RuntimeException("Friend request already sent");
                } else {
                    throw new RuntimeException("You already have a pending request from this user");
                }
            }
            friendship.setStatus(FriendshipStatus.PENDING);
            friendship.setRequesterId(senderId);
            friendship.setReceiverId(receiverId);
            friendship.setUpdatedAt(LocalDateTime.now());
            friendship = friendshipRepository.save(friendship);
        } else {
            friendship = Friendship.builder()
                    .requesterId(senderId)
                    .receiverId(receiverId)
                    .status(FriendshipStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            friendship = friendshipRepository.save(friendship);
        }
        
        log.info("Friend request sent from {} to {}", senderId, receiverId);
        return friendMapper.toResponse(friendship);
    }
    
    @Transactional
    public void acceptFriendRequest(String requestId, String userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));
                
        if (!friendship.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized to accept this request");
        }
        
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);
        
        conversationService.getOrCreatePrivateConversation(friendship.getRequesterId(), friendship.getReceiverId());
        log.info("Friendship ACCEPTED between {} and {}", friendship.getRequesterId(), friendship.getReceiverId());
    }
    
    @Transactional
    public void rejectFriendRequest(String requestId, String userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));
                
        if (!friendship.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized to reject this request");
        }
        
        friendship.setStatus(FriendshipStatus.DECLINED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);
        log.info("Friendship DECLINED between {} and {}", friendship.getRequesterId(), friendship.getReceiverId());
    }
    
    public List<FriendRequestResponse> getReceivedRequests(String userId) {
        return friendshipRepository.findByReceiverIdAndStatus(userId, FriendshipStatus.PENDING)
                .stream()
                .map(friendMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    public List<FriendRequestResponse> getSentRequests(String userId) {
        return friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING)
                .stream()
                .map(friendMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void unfriend(String userId1, String userId2) {
        Optional<Friendship> rel = friendshipRepository.findByRequesterIdAndReceiverId(userId1, userId2);
        rel.ifPresent(friendshipRepository::delete);
        log.info("Unfriended: {} and {}", userId1, userId2);
    }

    public List<UserResponse> getFriendsList(String userId) {
        List<Friendship> friendships = friendshipRepository.findAllAcceptedFriends(userId);
        
        return friendships.stream().map(f -> {
            String friendId = f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId();
            
            UserAuth auth = userAuthRepository.findById(friendId).orElse(null);
            UserDetail detail = userDetailRepository.findByUserId(friendId).orElse(null);
            
            return userMapper.toUserResponse(auth, detail, userId);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void blockUser(String blockerId, String blockedId) {
        Optional<Friendship> rel = friendshipRepository.findByRequesterIdAndReceiverId(blockerId, blockedId);
        
        if (rel.isPresent()) {
            Friendship f = rel.get();
            f.setRequesterId(blockerId);
            f.setReceiverId(blockedId);
            f.setStatus(FriendshipStatus.BLOCKED);
            f.setUpdatedAt(LocalDateTime.now());
            friendshipRepository.save(f);
        } else {
            Friendship newBlock = Friendship.builder()
                    .requesterId(blockerId)
                    .receiverId(blockedId)
                    .status(FriendshipStatus.BLOCKED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            friendshipRepository.save(newBlock);
        }
        log.info("User {} BLOCKED {}", blockerId, blockedId);
    }
}

