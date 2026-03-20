package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.FriendRequest;
import iuh.fit.enums.RequestStatus;

@Repository
public interface FriendRequestRepository extends MongoRepository<FriendRequest, String> {
    
    // Find pending requests sent by user
    List<FriendRequest> findBySenderIdAndStatus(String senderId, RequestStatus status);
    
    // Find pending requests received by user
    List<FriendRequest> findByReceiverIdAndStatus(String receiverId, RequestStatus status);
    
    // Check if request exists between two users
    Optional<FriendRequest> findBySenderIdAndReceiverId(String senderId, String receiverId);
    
    // Find all requests between two users (both directions)
    List<FriendRequest> findBySenderIdAndReceiverIdOrReceiverIdAndSenderId(
        String userId1, String userId2, String userId3, String userId4);
}
