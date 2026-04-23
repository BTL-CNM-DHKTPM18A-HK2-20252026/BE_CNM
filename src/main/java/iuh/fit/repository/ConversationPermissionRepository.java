package iuh.fit.repository;

import iuh.fit.entity.ConversationPermission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationPermissionRepository extends MongoRepository<ConversationPermission, String> {
    Optional<ConversationPermission> findByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);
}
