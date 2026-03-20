package iuh.fit.entity;

import java.util.Date;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * CallParticipant entity - Stores participants in a call
 * Related to: CallLog (callId), UserAuth (userId)
 */
@Document(collection = "call_participant")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallParticipant {
    
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    
    String callId; // Reference to CallLog
    
    String userId; // Reference to UserAuth (participant)
    Date joinedAt;
    Date leftAt;
    Date startedAt;
    Date endedAt;
    Integer durationSeconds; // Individual participation duration
}
