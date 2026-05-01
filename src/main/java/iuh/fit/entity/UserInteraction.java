package iuh.fit.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_interactions")
public class UserInteraction {
    @Id
    private String id;
    private String userId;
    private String targetId; // Post ID or Reel ID
    private String interactionType; // VIEW, CLICK, WATCH_COMPLETED, REWATCH, etc.
    private Double value; // e.g. watched duration in seconds
    private LocalDateTime createdAt;
}
