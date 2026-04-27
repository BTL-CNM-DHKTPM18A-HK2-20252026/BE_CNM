package iuh.fit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.PostComment;

@Repository
public interface PostCommentRepository extends MongoRepository<PostComment, String> {
    
    // Find comments by post
    Page<PostComment> findByPostIdAndIsDeletedFalseOrderByCreatedAtDesc(String postId, Pageable pageable);
    
    List<PostComment> findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(String postId);
    
    // Find replies to a comment
    List<PostComment> findByParentCommentIdAndIsDeletedFalse(String parentCommentId);
    
    // Count comments on a post
    long countByPostIdAndIsDeletedFalse(String postId);
}
