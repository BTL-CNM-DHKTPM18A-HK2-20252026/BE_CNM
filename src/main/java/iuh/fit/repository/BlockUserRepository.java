package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.BlockUser;

@Repository
public interface BlockUserRepository extends MongoRepository<BlockUser, String> {
    
    // Find all users blocked by a user
    List<BlockUser> findByBlockerId(String blockerId);
    
    // Check if user A blocked user B
    Optional<BlockUser> findByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);
    
    // Check if blocked in either direction
    Optional<BlockUser> findByBlockerIdAndBlockedUserIdOrBlockedUserIdAndBlockerId(
        String userId1, String userId2, String userId3, String userId4);
    
    // Delete block
    void deleteByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);
}
