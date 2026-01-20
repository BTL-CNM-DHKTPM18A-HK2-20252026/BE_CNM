package iuh.fit.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Story;

@Repository
public interface StoryRepository extends MongoRepository<Story, String> {
    
    // Find active stories by author
    List<Story> findByAuthorIdAndExpiresAtAfterAndIsDeletedFalse(
        String authorId, LocalDateTime currentTime);
    
    // Find all active stories from friends (within 24h)
    List<Story> findByAuthorIdInAndExpiresAtAfterAndIsDeletedFalse(
        List<String> friendIds, LocalDateTime currentTime);
    
    // Find expired stories to clean up
    List<Story> findByExpiresAtBeforeAndIsDeletedFalse(LocalDateTime currentTime);
}
