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
 * Report entity - Stores user reports for messages or conversations.
 */
@Document(collection = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Report {

    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();

    String reporterId; // User who submitted the report
    String conversationId; // Reported conversation (optional)
    String messageId; // Reported message (optional)
    String reason; // Report reason category
    String description; // Additional details from reporter
    LocalDateTime createdAt;

    @Builder.Default
    String status = "PENDING"; // PENDING, REVIEWED, RESOLVED, DISMISSED
}
