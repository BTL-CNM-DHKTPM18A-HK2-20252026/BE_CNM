package iuh.fit.dto.response.message;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private String messageId;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private String messageType;
    private String replyToMessageId;
    private Boolean isEdited;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
