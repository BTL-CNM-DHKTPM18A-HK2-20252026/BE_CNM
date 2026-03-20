package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.RequestStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * FriendRequest entity - Stores friend request information
 * Relationship: sender (userId) -> receiver (userId)
 */
@Document(collection = "friend_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequest {
    
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    
    String requestId; // Reference to user who sent the request
    String senderId;  // User who sent the request
    String receiverId; // User who received the request
    RequestStatus status;
    String message; // Optional message with request
    LocalDateTime sentAt;
    LocalDateTime responseAt;
    LocalDateTime expiredAt;
}
