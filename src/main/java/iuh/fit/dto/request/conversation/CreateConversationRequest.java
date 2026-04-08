package iuh.fit.dto.request.conversation;

import java.util.List;

import iuh.fit.enums.ConversationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    @NotNull(message = "Conversation type is required")
    private ConversationType conversationType;

    @Size(max = 100, message = "Conversation name must not exceed 100 characters")
    private String conversationName; // For group chat

    private String conversationAvatarUrl;

    private List<String> memberIds; // List of user IDs to add to conversation
}
