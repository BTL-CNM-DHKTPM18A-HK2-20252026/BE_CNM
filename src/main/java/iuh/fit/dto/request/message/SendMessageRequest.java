package iuh.fit.dto.request.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    // Optional for first P2P message, required if room exists
    private String conversationId;

    // Required for first P2P message
    private String recipientId;

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content must not exceed 5000 characters")
    private String content;

    private String messageType; // Enum string: TEXT, IMAGE, VIDEO, MEDIA

    private String replyToMessageId; // Optional: for replying to a message

    private String fileName;
    private Long fileSize;
    private Integer voiceDuration;
    private Integer videoDuration;
    private String forwardedFromMessageId;
}
