package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.FriendShip;

@Repository
public interface FriendShipRepository extends MongoRepository<FriendShip, String> {
    
    // Find all friends of a user
    List<FriendShip> findByUserId1OrUserId2(String userId1, String userId2);
    
    // Check if two users are friends
    Optional<FriendShip> findByUserId1AndUserId2(String userId1, String userId2);
    
    // Count friends of a user
    long countByUserId1OrUserId2(String userId1, String userId2);
    
    // Delete friendship
    void deleteByUserId1AndUserId2(String userId1, String userId2);
}
