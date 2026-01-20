package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.CallParticipant;

@Repository
public interface CallParticipantRepository extends MongoRepository<CallParticipant, String> {
    
    // Find all participants in a call
    List<CallParticipant> findByCallId(String callId);
    
    // Find participant by call and user
    Optional<CallParticipant> findByCallIdAndUserId(String callId, String userId);
    
    // Count participants in a call
    long countByCallId(String callId);
}
