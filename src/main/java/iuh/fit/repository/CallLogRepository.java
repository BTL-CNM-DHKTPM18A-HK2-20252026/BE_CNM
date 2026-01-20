package iuh.fit.repository;

import iuh.fit.entity.CallLog;
import iuh.fit.enums.CallStatus;
import iuh.fit.enums.CallType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CallLogRepository extends MongoRepository<CallLog, String> {
    
    // Find calls in a conversation
    Page<CallLog> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
    
    // Find calls by initiator
    List<CallLog> findByInitiatorId(String initiatorId);
    
    // Find calls by type
    List<CallLog> findByConversationIdAndCallType(String conversationId, CallType callType);
    
    // Find missed calls
    List<CallLog> findByConversationIdAndCallStatus(String conversationId, CallStatus status);
}
