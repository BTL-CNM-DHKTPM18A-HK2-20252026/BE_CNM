package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Conversations;
import iuh.fit.enums.ConversationType;

@Repository
public interface ConversationRepository extends MongoRepository<Conversations, String> {
    
    // Find private conversation by the exact members (sorted) to support Lazy Creation
    @Query("{ 'participants': { $all: ?0, $size: 2 }, 'conversationType': 'PRIVATE' }")
    Optional<Conversations> findPrivateConversation(List<String> participants);
    
    // Find conversations by type
    List<Conversations> findByConversationType(ConversationType conversationType);
    
    // Find conversation by name (for group chat)
    Optional<Conversations> findByConversationName(String conversationName);
    
    // Find active conversations
    List<Conversations> findByIsDeletedFalse();
}
