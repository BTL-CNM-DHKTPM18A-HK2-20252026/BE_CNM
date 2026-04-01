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
public class AiChatResponse {
    private MessageResponse userMessage;
    private MessageResponse imageMessage;
    private MessageResponse assistantMessage;
    private ConversationResponse conversation;
    private boolean fallbackUsed;
    private String providerStatus;
}
