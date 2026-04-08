package iuh.fit.service.ai;

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
public class BlackboxAiClient {

    private final ObjectMapper objectMapper;

    @Value("${ai.blackbox.url:https://api.blackbox.ai/v1/chat/completions}")
    private String blackboxUrl;

    @Value("${BLACKBOX_API_KEY:}")
    private String apiKey;

    @Value("${BLACKBOX_MODEL:blackboxai/blackbox-pro}")
    private String defaultModel;

    @Value("${ai.model.chat:blackboxai/blackbox-pro}")
    private String fallbackModel;

    @Value("${ai.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${ai.max-output-tokens:1000}")
    private int maxOutputTokens;

    public AiCompletionResult complete(List<Map<String, String>> messages, String model) {
        return complete(messages, model, maxOutputTokens);
    }

    /**
     * Call the AI completion API with a custom max_tokens limit.
     * Used by the router (small budget) and the main call (full budget).
     */
    public AiCompletionResult complete(List<Map<String, String>> messages, String model, int maxTokens) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("BLACKBOX_API_KEY is missing");
        }

        String finalModel = StringUtils.hasText(model) ? model : defaultModel;

        try {
            return doComplete(messages, finalModel, maxTokens);
        } catch (HttpClientErrorException.BadRequest ex) {
            boolean invalidModel = ex.getResponseBodyAsString() != null
                    && ex.getResponseBodyAsString().toLowerCase().contains("invalid model name");
            boolean canFallback = invalidModel && StringUtils.hasText(fallbackModel)
                    && !fallbackModel.equals(finalModel);

            if (!canFallback) {
                throw ex;
            }

            log.warn("Model '{}' is invalid for Blackbox API. Retrying with fallback model '{}'.",
                    finalModel, fallbackModel);
            return doComplete(messages, fallbackModel, maxTokens);
        }
    }

    private AiCompletionResult doComplete(List<Map<String, String>> messages, String model, int maxTokens) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("max_tokens", Math.max(1, maxTokens));
        payload.put("stream", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = getRestTemplate().postForEntity(blackboxUrl, request, String.class);

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
            String fromMessage = first.path("message").path("content").asText(null);
            if (StringUtils.hasText(fromMessage)) {
                return fromMessage;
            }
            String fromText = first.path("text").asText(null);
            if (StringUtils.hasText(fromText)) {
                return fromText;
            }
        }

        String outputText = root.path("output_text").asText(null);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        String response = root.path("response").asText(null);
        if (StringUtils.hasText(response)) {
            return response;
        }

        return null;
    }

    public boolean isTimeout(Throwable ex) {
        return ex instanceof ResourceAccessException
                && ex.getMessage() != null
                && ex.getMessage().toLowerCase().contains("timed out");
    }
}
