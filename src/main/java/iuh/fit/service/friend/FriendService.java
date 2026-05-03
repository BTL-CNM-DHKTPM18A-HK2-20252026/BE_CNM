package iuh.fit.service.friend;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.service.notification.NotificationEvent;

import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.dto.response.friend.FriendSuggestionResponse;
import iuh.fit.dto.response.user.UserResponse;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.FriendshipStatus;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.ForbiddenException;
import iuh.fit.mapper.FriendMapper;
import iuh.fit.mapper.UserMapper;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendService implements IFriendService {

    private final FriendshipRepository friendshipRepository;
    private final ConversationService conversationService;
    private final FriendMapper friendMapper;
    private final UserAuthRepository userAuthRepository;
    private final UserDetailRepository userDetailRepository;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserSettingRepository userSettingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FriendRequestResponse sendFriendRequest(String senderId, String receiverId, String message) {
        // 0. Cannot send friend request to yourself
        if (senderId.equals(receiverId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_YOURSELF);
        }

        // 0.5 Check if receiver's account is locked
        UserSetting receiverSetting = userSettingRepository.findById(receiverId).orElse(null);
        if (receiverSetting != null && Boolean.TRUE.equals(receiverSetting.getAccountLocked())) {
            throw new ForbiddenException(ErrorCode.USER_ACCOUNT_LOCKED);
        }

        // 1. Check if receiver has BLOCKED sender
        Optional<Friendship> blockCheck = friendshipRepository.findByRequesterIdAndReceiverIdAndStatus(receiverId,
                senderId, FriendshipStatus.BLOCKED);
        if (blockCheck.isPresent()) {
            throw new ForbiddenException(ErrorCode.USER_BLOCKED_YOU);
        }

        // 2. Check existing relationship
        Optional<Friendship> existingRel = friendshipRepository.findByRequesterIdAndReceiverId(senderId, receiverId);

        Friendship friendship;
        if (existingRel.isPresent()) {
            friendship = existingRel.get();
            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new AppException(ErrorCode.ALREADY_FRIENDS);
            }
            if (friendship.getStatus() == FriendshipStatus.PENDING) {
                if (friendship.getRequesterId().equals(senderId)) {
                    log.info("Friend request already exists and is PENDING from {} to {}", senderId, receiverId);
                    return friendMapper.toResponse(friendship); // Just return existing
                } else {
                    throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_FROM_RECEIVER);
                }
            }
            friendship.setStatus(FriendshipStatus.PENDING);
            friendship.setRequesterId(senderId);
            friendship.setReceiverId(receiverId);
            friendship.setMessage(message);
            friendship.setUpdatedAt(LocalDateTime.now());
            friendship = friendshipRepository.save(friendship);
        } else {
            friendship = Friendship.builder()
                    .requesterId(senderId)
                    .receiverId(receiverId)
                    .status(FriendshipStatus.PENDING)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            friendship = friendshipRepository.save(friendship);
        }

        log.info("Friend request sent from {} to {}", senderId, receiverId);

        // Notify receiver
        messagingTemplate.convertAndSend("/topic/friend-events/" + receiverId, "RECEIVED");
        // Notify sender (for real-time update on other devices/tabs)
        messagingTemplate.convertAndSend("/topic/friend-events/" + senderId, "SENT");

        // Publish notification event (async persist + push)
        eventPublisher.publishEvent(NotificationEvent.forFriendRequest(
                this, receiverId, senderId, friendship.getId()));

        return friendMapper.toResponse(friendship);
    }

    @Transactional
    public void acceptFriendRequest(String requestId, String userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        if (!friendship.getReceiverId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.NOT_AUTHORIZED_TO_HANDLE_REQUEST);
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        conversationService.getOrCreatePrivateConversation(friendship.getRequesterId(), friendship.getReceiverId());
        log.info("Friendship ACCEPTED between {} and {}", friendship.getRequesterId(), friendship.getReceiverId());

        // Notify both users to update friend list
        messagingTemplate.convertAndSend("/topic/friend-events/" + friendship.getRequesterId(), "ACCEPTED");
        messagingTemplate.convertAndSend("/topic/friend-events/" + friendship.getReceiverId(), "ACCEPTED");

        // Notify original requester rằng người ta đã chấp nhận
        eventPublisher.publishEvent(NotificationEvent.forFriendAccepted(
                this, friendship.getRequesterId(), friendship.getReceiverId(), friendship.getId()));
    }

    @Transactional
    public void rejectFriendRequest(String requestId, String userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        if (!friendship.getReceiverId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.NOT_AUTHORIZED_TO_HANDLE_REQUEST);
        }

        friendship.setStatus(FriendshipStatus.DECLINED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);
        log.info("Friendship DECLINED between {} and {}", friendship.getRequesterId(), friendship.getReceiverId());

        // Notify user to update UI (other devices)
        messagingTemplate.convertAndSend("/topic/friend-events/" + userId, "REJECTED");
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
        if (!rel.isPresent()) {
            rel = friendshipRepository.findByRequesterIdAndReceiverId(userId2, userId1);
        }
        rel.ifPresent(friendshipRepository::delete);
        log.info("Unfriended: {} and {}", userId1, userId2);

        // Notify both users to update UI
        messagingTemplate.convertAndSend("/topic/friend-events/" + userId1, "UNFRIENDED");
        messagingTemplate.convertAndSend("/topic/friend-events/" + userId2, "UNFRIENDED");
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

        // Notify both users
        messagingTemplate.convertAndSendToUser(blockerId, "/queue/friend-updates", "BLOCKED");
        messagingTemplate.convertAndSendToUser(blockedId, "/queue/friend-updates", "BLOCKED");
    }

    public List<String> getFriendsIds(String userId) {
        List<Friendship> friendships = friendshipRepository.findAllAcceptedFriends(userId);
        return friendships.stream().map(f -> f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId())
                .collect(Collectors.toList());
    }

    public List<FriendSuggestionResponse> getFriendSuggestions(String userId, int limit) {
        // 1. Get current friends
        List<String> myFriends = getFriendsIds(userId);
        UserAuth currentUser = userAuthRepository.findById(userId).orElse(null);
        Set<String> excludeIds = new HashSet<>(myFriends);
        excludeIds.add(userId);

        // Add dismissed suggestions to exclusion list
        if (currentUser != null && currentUser.getDismissedSuggestionIds() != null) {
            excludeIds.addAll(currentUser.getDismissedSuggestionIds());
        }

        // 2. Get friends of friends
        Map<String, Integer> mutualFriendsCount = new HashMap<>();
        Map<String, List<String>> mutualFriendsNames = new HashMap<>();

        for (String friendId : myFriends) {
            List<String> friendsOfFriend = getFriendsIds(friendId);
            for (String fofId : friendsOfFriend) {
                if (!excludeIds.contains(fofId)) {
                    mutualFriendsCount.put(fofId, mutualFriendsCount.getOrDefault(fofId, 0) + 1);

                    // Get friend's name for reasoning
                    UserDetail friendDetail = userDetailRepository.findByUserId(friendId).orElse(null);
                    String friendName = friendDetail != null
                            ? (friendDetail.getDisplayName() != null ? friendDetail.getDisplayName() : "Người dùng")
                            : "Người dùng";

                    mutualFriendsNames.computeIfAbsent(fofId, k -> new ArrayList<>()).add(friendName);
                }
            }
        }

        // 3. Fallback: If no mutual friends found (cold start), suggest any active
        // users
        if (mutualFriendsCount.isEmpty()) {
            List<UserAuth> allUsers = userAuthRepository.findAll();
            log.info("[FriendService] No mutual friends found for {}. Total users in DB: {}", userId, allUsers.size());

            return allUsers.stream()
                    .filter(auth -> !excludeIds.contains(auth.getUserId()))
                    .filter(auth -> !Boolean.TRUE.equals(auth.getIsDeleted()))
                    .limit(limit)
                    .map(auth -> {
                        UserDetail detail = userDetailRepository.findByUserId(auth.getUserId()).orElse(null);
                        return FriendSuggestionResponse.builder()
                                .userId(auth.getUserId())
                                .fullName(detail != null ? detail.getDisplayName() : "Người dùng Fruvia")
                                .username(auth.getPhoneNumber())
                                .avatarUrl(detail != null ? detail.getAvatarUrl() : "")
                                .mutualFriendCount(0)
                                .reason("Gợi ý cho bạn")
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        // 4. Sort and map to response
        return mutualFriendsCount.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    String candidateId = entry.getKey();
                    UserAuth auth = userAuthRepository.findById(candidateId).orElse(null);
                    UserDetail detail = userDetailRepository.findByUserId(candidateId).orElse(null);

                    List<String> names = mutualFriendsNames.get(candidateId);
                    String reason = "";
                    if (names != null && !names.isEmpty()) {
                        reason = names.size() > 1
                                ? names.get(0) + " và " + (names.size() - 1) + " bạn chung"
                                : names.get(0) + " là bạn chung";
                    } else {
                        reason = "Gợi ý cho bạn";
                    }

                    return FriendSuggestionResponse.builder()
                            .userId(candidateId)
                            .fullName(detail != null ? detail.getDisplayName() : "")
                            .username(auth != null ? auth.getPhoneNumber() : "")
                            .avatarUrl(detail != null ? detail.getAvatarUrl() : "")
                            .mutualFriendCount(entry.getValue())
                            .mutualFriendNames(names)
                            .reason(reason)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void dismissSuggestion(String userId, String dismissedId) {
        userAuthRepository.findById(userId).ifPresent(user -> {
            if (user.getDismissedSuggestionIds() == null) {
                user.setDismissedSuggestionIds(new ArrayList<>());
            }
            if (!user.getDismissedSuggestionIds().contains(dismissedId)) {
                user.getDismissedSuggestionIds().add(dismissedId);
                userAuthRepository.save(user);
            }
        });
        log.info("User {} DISMISSED suggestion {}", userId, dismissedId);
    }
}
