package iuh.fit.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCompletionResult {
    private String content;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String providerRequestId;
    private String model;
}
