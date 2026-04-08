package iuh.fit.dto.response.message;

import iuh.fit.dto.response.conversation.ConversationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAndConversationResponse {
    private MessageResponse message;
    private ConversationResponse conversation; // Combined metadata for Sidebar update
}
