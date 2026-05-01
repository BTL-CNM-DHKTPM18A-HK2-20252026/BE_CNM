package iuh.fit.service.ai;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for AI completion providers.
 * Enables swapping BlackboxAI for any other provider without changing callers.
 */
public interface AiCompletionProvider {

    /**
     * Complete a conversation using the default max_tokens budget.
     */
    AiCompletionResult complete(List<Map<String, String>> messages, String model);

    /**
     * Complete a conversation with an explicit max_tokens budget.
     */
    AiCompletionResult complete(List<Map<String, String>> messages, String model, int maxTokens);

    /**
     * Returns true if the given throwable represents a timeout.
     */
    boolean isTimeout(Throwable ex);
}
