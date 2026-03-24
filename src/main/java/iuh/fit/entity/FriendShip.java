package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.FriendshipStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Friendship entity - Stores relationship states between two users
 * Manages the entire lifecycle: PENDING, ACCEPTED, DECLINED, BLOCKED
 */
@Document(collection = "friendships")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Friendship {
    
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    
    String requesterId; // User who initiated the relationship/request
    String receiverId;  // User who received the relationship/request
    
    FriendshipStatus status; // PENDING, ACCEPTED, DECLINED, BLOCKED
    
    String message; // Optional message from sender
    
    LocalDateTime createdAt; // Time of first interaction/request
    LocalDateTime updatedAt; // Time of last status change
}
