package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.StoryView;

@Repository
public interface StoryViewRepository extends MongoRepository<StoryView, String> {
    
    // Find all views on a story
    List<StoryView> findByStoryId(String storyId);
    
    // Check if user viewed a story
    Optional<StoryView> findByStoryIdAndViewerId(String storyId, String viewerId);

    boolean existsByStoryIdAndViewerId(String storyId, String viewerId);
    
    // Count views on a story
    long countByStoryId(String storyId);
}
