package iuh.fit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Post;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    
    // Find posts by author
    Page<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId, Pageable pageable);
    
    // Find all public posts
    Page<Post> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
    
    // Find posts by author list (for news feed)
    Page<Post> findByAuthorIdInAndIsDeletedFalseOrderByCreatedAtDesc(List<String> authorIds, Pageable pageable);
}
