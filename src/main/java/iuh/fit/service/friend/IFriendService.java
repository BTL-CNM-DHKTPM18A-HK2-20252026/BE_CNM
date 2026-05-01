package iuh.fit.service.friend;

import java.util.List;

import iuh.fit.dto.response.friend.FriendRequestResponse;
import iuh.fit.dto.response.friend.FriendSuggestionResponse;
import iuh.fit.dto.response.user.UserResponse;

public interface IFriendService {

    FriendRequestResponse sendFriendRequest(String senderId, String receiverId, String message);

    void acceptFriendRequest(String requestId, String userId);

    void rejectFriendRequest(String requestId, String userId);

    List<FriendRequestResponse> getReceivedRequests(String userId);

    List<FriendRequestResponse> getSentRequests(String userId);

    void unfriend(String userId1, String userId2);

    List<UserResponse> getFriendsList(String userId);

    void blockUser(String blockerId, String blockedId);

    List<String> getFriendsIds(String userId);

    List<FriendSuggestionResponse> getFriendSuggestions(String userId, int limit);

    void dismissSuggestion(String userId, String dismissedId);
}
