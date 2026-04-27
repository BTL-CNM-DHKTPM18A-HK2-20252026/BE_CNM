package iuh.fit.repository;

import iuh.fit.entity.CommentReaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentReactionRepository extends MongoRepository<CommentReaction, String> {
    Optional<CommentReaction> findByCommentIdAndUserId(String commentId, String userId);
    List<CommentReaction> findByCommentId(String commentId);
    void deleteByCommentIdAndUserId(String commentId, String userId);
    long countByCommentId(String commentId);
}
