package iuh.fit.repository;

import iuh.fit.entity.PostReaction;
import iuh.fit.enums.ReactionType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostReactionRepository extends MongoRepository<PostReaction, String> {
    
    // Find all reactions on a post
    List<PostReaction> findByPostId(String postId);
    
    // Find user's reaction on a post
    Optional<PostReaction> findByPostIdAndUserId(String postId, String userId);
    
    // Count reactions by type on a post
    long countByPostIdAndReactionType(String postId, ReactionType reactionType);
    
    // Delete user's reaction on a post
    void deleteByPostIdAndUserId(String postId, String userId);
}
