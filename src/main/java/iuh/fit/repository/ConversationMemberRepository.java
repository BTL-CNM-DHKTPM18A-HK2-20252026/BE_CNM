package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.ConversationMember;
import iuh.fit.enums.MemberRole;

@Repository
public interface ConversationMemberRepository extends MongoRepository<ConversationMember, String> {
    
    // Find all members in a conversation
    List<ConversationMember> findByConversationId(String conversationId);
    
    // Find all conversations of a user
    List<ConversationMember> findByUserId(String userId);
    
    // Check if user is member of conversation
    Optional<ConversationMember> findByConversationIdAndUserId(String conversationId, String userId);
    
    // Find admins of a conversation
    List<ConversationMember> findByConversationIdAndRole(String conversationId, MemberRole role);
    
    // Count members in conversation
    long countByConversationId(String conversationId);
}
