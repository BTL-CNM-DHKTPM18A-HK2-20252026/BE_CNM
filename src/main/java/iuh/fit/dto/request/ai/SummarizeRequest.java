package iuh.fit.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummarizeRequest {

    @NotBlank(message = "conversationId is required")
    private String conversationId;

    /**
     * Optional: if provided, summarize only messages after this ID.
     * If null, summarize from the user's lastReadMessageId.
     */
    private String lastReadMessageId;

    /**
     * Optional: number of recent messages to summarize (for /summarize-recent).
     * Defaults to 100 if not provided.
     */
    private Integer messageCount;
}
