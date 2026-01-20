package iuh.fit.entity;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * CallLog entity - Stores call history
 * Related to: UserAuth (callers), Conversations (conversationId)
 * Can have: CallParticipant
 */
@Document(collection = "call_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallLog {
    
    @Id
    String callId;
    
    String callerId; // Reference to UserAuth (who initiated the call)
    String conversationId; // Reference to Conversations
    String type; // AUDIO, VIDEO
    String status; // COMPLETED, MISSED, REJECTED, CANCELLED
    Date startedAt;
    Date endedAt;
    Integer durationSeconds; // Call duration
}
