package iuh.fit.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import iuh.fit.entity.Reel;

@Repository
public interface ReelRepository extends MongoRepository<Reel, String> {
    
    List<Reel> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId);
    
    List<Reel> findByIsDeletedFalseOrderByCreatedAtDesc();
    
    long countByAuthorIdAndIsDeletedFalse(String authorId);
}
