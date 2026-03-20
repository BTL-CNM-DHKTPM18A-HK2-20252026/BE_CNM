package iuh.fit.entity;

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
    @Builder.Default
    String callId = UUID.randomUUID().toString();

    String initiatorId; // Reference to UserAuth (who initiated the call)
    String conversationId; // Reference to Conversations
    iuh.fit.enums.CallType callType; // AUDIO, VIDEO
    iuh.fit.enums.CallStatus callStatus; // COMPLETED, MISSED, REJECTED, CANCELLED
    java.time.LocalDateTime createdAt;
    java.time.LocalDateTime startedAt;
    java.time.LocalDateTime endedAt;
    Integer durationSeconds; // Call duration
}
