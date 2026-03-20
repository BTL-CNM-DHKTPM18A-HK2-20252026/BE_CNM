package iuh.fit.entity;

import java.time.LocalDateTime;
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
 * FriendShip entity - Stores friendship relationships
 * Bidirectional relationship between two users
 */
@Document(collection = "friend_ship")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendShip {
    
    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();
    
    String userId1; // First user in friendship
    String userId2; // Second user in friendship
    LocalDateTime createdAt; // When they became friends
}
