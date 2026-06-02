package iuh.fit.service.ai.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiCompletionClient implements AiCompletionProvider {

    private final ObjectMapper objectMapper;

    // ── OpenAI config ──────────────────────────────────────────────────
    @Value("${ai.openai.url:https://api.openai.com/v1/chat/completions}")
    private String openaiUrl;

    @Value("${OPENAI_API_KEY:}")
    private String openaiApiKey;

    @Value("${OPENAI_MODEL:gpt-4o}")
    private String openaiModel;

    // ── DeepSeek config (OpenAI-compatible, fallback) ──────────────────
    @Value("${ai.deepseek.url:https://api.deepseek.com/v1/chat/completions}")
    private String deepseekUrl;

    @Value("${DEEPSEEK_API_KEY:}")
    private String deepseekApiKey;

    @Value("${DEEPSEEK_MODEL:deepseek-v4-pro}")
    private String deepseekModel;

    @Value("${ai.model.chat:gpt-4o}")
    private String fallbackModel;

    @Value("${ai.timeout-ms:60000}")
    private int timeoutMs;

    @Value("${ai.max-output-tokens:2000}")
    private int maxOutputTokens;

    // ── Resolved provider (lazy) ───────────────────────────────────────
    private volatile ProviderConfig resolvedProvider;
    private final Object providerLock = new Object();

    private record ProviderConfig(String url, String key, String model) {
    }

    private ProviderConfig resolveProvider() {
        if (resolvedProvider != null)
            return resolvedProvider;
        synchronized (providerLock) {
            if (resolvedProvider != null)
                return resolvedProvider;
            // Ưu tiên DeepSeek, fallback OpenAI
            if (StringUtils.hasText(deepseekApiKey)) {
                resolvedProvider = new ProviderConfig(deepseekUrl, deepseekApiKey, deepseekModel);
                log.info("AI Provider: DeepSeek (model={})", deepseekModel);
            } else if (StringUtils.hasText(openaiApiKey)) {
                resolvedProvider = new ProviderConfig(openaiUrl, openaiApiKey, openaiModel);
                log.info("AI Provider: OpenAI (model={})", openaiModel);
            } else {
                throw new IllegalStateException(
                        "No AI API key configured. Set OPENAI_API_KEY or DEEPSEEK_API_KEY.");
            }
            return resolvedProvider;
        }
    }

    public AiCompletionResult complete(List<Map<String, String>> messages, String model) {
        return complete(messages, model, maxOutputTokens);
    }

    /**
     * Call the OpenAI-compatible completion API with a custom max_tokens limit.
     * Used by the router (small budget) and the main call (full budget).
     */
    public AiCompletionResult complete(List<Map<String, String>> messages, String model, int maxTokens) {
        ProviderConfig provider = resolveProvider();

        String finalModel = StringUtils.hasText(model) ? model : provider.model();

        try {
            return doComplete(provider, messages, finalModel, maxTokens);
        } catch (HttpClientErrorException.BadRequest ex) {
            boolean invalidModel = ex.getResponseBodyAsString() != null
                    && ex.getResponseBodyAsString().toLowerCase().contains("invalid model name");
            boolean canFallback = invalidModel && StringUtils.hasText(fallbackModel)
                    && !fallbackModel.equals(finalModel);

            if (!canFallback) {
                throw ex;
            }

            log.warn("Model '{}' is invalid for AI API. Retrying with fallback model '{}'.",
                    finalModel, fallbackModel);
            return doComplete(provider, messages, fallbackModel, maxTokens);
        }
    }

    /**
     * Vision-optimised completion: sets temperature=0.1 for maximum factual
     * grounding. The messages list should already contain image_url content blocks.
     */
    public AiCompletionResult completeVision(List<Map<String, Object>> messages, String model, int maxTokens) {
        ProviderConfig provider = resolveProvider();

        String finalModel = StringUtils.hasText(model) ? model : provider.model();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.key());

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", finalModel);
        payload.put("messages", messages);
        payload.put("max_tokens", Math.max(1, maxTokens));
        payload.put("stream", false);
        payload.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = getRestTemplate().postForEntity(provider.url(), request, String.class);

        return parseResponse(response.getBody(), finalModel);
    }

    private AiCompletionResult doComplete(ProviderConfig provider, List<Map<String, String>> messages, String model,
            int maxTokens) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.key());

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("max_tokens", Math.max(1, maxTokens));
        payload.put("stream", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = getRestTemplate().postForEntity(provider.url(), request, String.class);

        return parseResponse(response.getBody(), model);
    }

    private RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private AiCompletionResult parseResponse(String body, String model) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = extractContent(root);
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("AI response content is empty");
            }

            String finishReason = null;
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                finishReason = choices.get(0).path("finish_reason").asText(null);
            }

            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);

            return AiCompletionResult.builder()
                    .content(content)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .providerRequestId(root.path("id").asText(null))
                    .model(model)
                    .finishReason(finishReason)
                    .build();
        } catch (Exception ex) {
            log.error("Failed parsing AI response: {}", ex.getMessage());
            throw new IllegalStateException("Failed parsing AI response", ex);
        }
    }

    private String extractContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            JsonNode messageNode = first.path("message");

            // Ưu tiên lấy content trước
            String fromMessage = messageNode.path("content").asText(null);
            // Nếu content rỗng/null nhưng có reasoning_content → reasoning model thuần
            String fromReasoning = messageNode.path("reasoning_content").asText(null);

            if (StringUtils.hasText(fromMessage)) {
                // Strip reasoning tags nếu model tự động gộp reasoning vào content
                return stripReasoningContent(fromMessage);
            }
            // DeepSeek-R1 / reasoning models: reasoning_content là primary output
            if (StringUtils.hasText(fromReasoning)) {
                return stripReasoningContent(fromReasoning);
            }
            String fromText = first.path("text").asText(null);
            if (StringUtils.hasText(fromText)) {
                return stripReasoningContent(fromText);
            }
            // Some models return delta.content (streaming format even on non-stream)
            String fromDelta = first.path("delta").path("content").asText(null);
            if (StringUtils.hasText(fromDelta)) {
                return stripReasoningContent(fromDelta);
            }
        }

        String outputText = root.path("output_text").asText(null);
        if (StringUtils.hasText(outputText)) {
            return stripReasoningContent(outputText);
        }

        String response = root.path("response").asText(null);
        if (StringUtils.hasText(response)) {
            return stripReasoningContent(response);
        }

        return null;
    }

    /**
     * Loại bỏ reasoning tags và phần suy luận của reasoning model
     * (DeepSeek-R1, o1, o3, v.v.) khỏi content trước khi trả về cho user.
     */
    private String stripReasoningContent(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        // Xử lý các tag phổ biến của reasoning models
        // 1) ... (DeepSeek-R1, Qwen, v.v.)
        content = content.replaceAll("(?s)ˋ{3}thinkˋ{3}.*?ˋ{3}thinkˋ{3}", "").trim();
        content = content.replaceAll("(?s)```think```.*?```think```", "").trim();
        // 2) ... hoặc ...
        content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        // 3) ...
        content = content.replaceAll("(?s)ˋ{3}reasoningˋ{3}.*?ˋ{3}reasoningˋ{3}", "").trim();
        content = content.replaceAll("(?s)<reasoning>.*?</reasoning>", "").trim();
        // 4) ... (OpenAI o1/o3 style)
        content = content.replaceAll("(?s)<antThink>.*?</antThink>", "").trim();
        // 5) Nếu content bắt đầu bằng "Ta cần phân tích", "Tôi cần phân tích" →
        // reasoning leak
        if (content.length() > 200) {
            String lower = content.toLowerCase();
            // Pattern: "Tôi là... Ta cần phân tích..." → cắt bỏ phần reasoning phía sau
            String[] reasoningMarkers = {
                    "ta cần phân tích",
                    "tôi cần phân tích",
                    "trước hết",
                    "trước tiên",
                    "hãy phân tích",
                    "let me analyze",
                    "let's think",
                    "i need to analyze",
                    "first, let me"
            };
            for (String marker : reasoningMarkers) {
                int idx = lower.indexOf(marker, 50); // chỉ tìm sau 50 ký tự đầu (bỏ qua câu trả lời ngắn)
                if (idx > 0) {
                    String before = content.substring(0, idx).trim();
                    // Chỉ cắt nếu phần trước đã là 1 câu hoàn chỉnh (kết thúc bằng .!?)
                    if (before.matches(".*[.!?]\\s*$") && before.length() > 20) {
                        return before;
                    }
                }
            }
        }
        return content;
    }

    public boolean isTimeout(Throwable ex) {
        return ex instanceof ResourceAccessException
                && ex.getMessage() != null
                && ex.getMessage().toLowerCase().contains("timed out");
    }
}
