package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Friendship;
import iuh.fit.enums.FriendshipStatus;

@Repository
public interface FriendshipRepository extends MongoRepository<Friendship, String> {
    
    // Find relationship between two users (regardless of order)
    @Query("{ $or: [ { 'requesterId': ?0, 'receiverId': ?1 }, { 'requesterId': ?1, 'receiverId': ?0 } ] }")
    Optional<Friendship> findByRequesterIdAndReceiverId(String user1, String user2);
    
    // Find specifically if userA sent request to userB (ordered)
    Optional<Friendship> findByRequesterIdAndReceiverIdAndStatus(String requesterId, String receiverId, FriendshipStatus status);
    
    // Check if B has BLOCKED A (Specifically requester=B, receiver=A, status=BLOCKED)
    Optional<Friendship> findByRequesterIdAndReceiverIdAndStatus(String blockerId, String blockedId, String status); // Overloaded but we can use specific ones
    
    // For friends list: all relationships with status=ACCEPTED where user is either requester or receiver
    @Query("{ 'status': 'ACCEPTED', $or: [ { 'requesterId': ?0 }, { 'receiverId': ?0 } ] }")
    List<Friendship> findAllAcceptedFriends(String userId);

    // For counts
    @Query(value = "{ 'status': 'ACCEPTED', $or: [ { 'requesterId': ?0 }, { 'receiverId': ?0 } ] }", count = true)
    long countAcceptedFriends(String userId);

    // Find all pending requests for a user (as receiver)
    List<Friendship> findByReceiverIdAndStatus(String receiverId, FriendshipStatus status);

    // Find all pending requests sent by a user (as requester)
    List<Friendship> findByRequesterIdAndStatus(String requesterId, FriendshipStatus status);
}
