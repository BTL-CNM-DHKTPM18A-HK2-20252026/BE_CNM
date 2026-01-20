package iuh.fit.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * MessageAttachment entity - Stores file attachments for messages
 * Related to: Message (messageId)
 */
@Document(collection = "message_attachment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageAttachment {
    
    @Id
    String attachmentId;
    
    String messageId; // Reference to Message
    String url; // File URL
    String fileName;
    Long fileSize;
    String thumbnailUrl; // For images/videos
}
