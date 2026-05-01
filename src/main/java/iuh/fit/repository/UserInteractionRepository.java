package iuh.fit.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import iuh.fit.entity.UserInteraction;

@Repository
public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {
    List<UserInteraction> findByUserId(String userId);
    List<UserInteraction> findByUserIdAndInteractionType(String userId, String interactionType);
    long countByUserIdAndTargetIdAndInteractionType(String userId, String targetId, String interactionType);
}
