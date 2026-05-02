package iuh.fit.service.ai.core;

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
    private String finishReason;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setPromptTokens(int tokens) {
        this.promptTokens = tokens;
    }

    public void setCompletionTokens(int tokens) {
        this.completionTokens = tokens;
    }

    public void setTotalTokens(int tokens) {
        this.totalTokens = tokens;
    }

    public void setProviderRequestId(String id) {
        this.providerRequestId = id;
    }

    public void setFinishReason(String reason) {
        this.finishReason = reason;
    }

    public static AiCompletionResultBuilder builder() {
        return new AiCompletionResultBuilder();
    }

    public static class AiCompletionResultBuilder {
        private final AiCompletionResult result = new AiCompletionResult();

        public AiCompletionResultBuilder content(String content) {
            result.setContent(content);
            return this;
        }

        public AiCompletionResultBuilder promptTokens(int tokens) {
            result.setPromptTokens(tokens);
            return this;
        }

        public AiCompletionResultBuilder completionTokens(int tokens) {
            result.setCompletionTokens(tokens);
            return this;
        }

        public AiCompletionResultBuilder totalTokens(int tokens) {
            result.setTotalTokens(tokens);
            return this;
        }

        public AiCompletionResultBuilder providerRequestId(String id) {
            result.setProviderRequestId(id);
            return this;
        }

        public AiCompletionResultBuilder model(String model) {
            result.setModel(model);
            return this;
        }

        public AiCompletionResultBuilder finishReason(String reason) {
            result.setFinishReason(reason);
            return this;
        }

        public AiCompletionResult build() {
            return result;
        }
    }
}
