package iuh.fit.entity;

import java.time.LocalDateTime;

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
    String id;
    
    String userId1; // First user in friendship
    String userId2; // Second user in friendship
    LocalDateTime createdAt; // When they became friends
}
