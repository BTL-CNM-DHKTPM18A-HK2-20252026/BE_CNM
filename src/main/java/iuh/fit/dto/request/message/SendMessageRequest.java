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
    
    @NotBlank(message = "Conversation ID is required")
    private String conversationId;
    
    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content must not exceed 5000 characters")
    private String content;
    
    private String replyToMessageId; // Optional: for replying to a message
}
