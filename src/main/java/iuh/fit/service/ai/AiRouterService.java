package iuh.fit.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Router – classifies user intent and selects the optimal model.
 * <p>
 * Step 1: Send user message + router system prompt to a fast/cheap model.
 * Step 2: Parse the JSON response to get selected_model, task_type,
 * refined_prompt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiRouterService {

    private final AiCompletionProvider blackboxAiClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.router.model:blackboxai/minimax-free}")
    private String routerModel;

    @Value("${ai.model.chat:blackboxai/blackbox-pro}")
    private String modelChat;

    @Value("${ai.model.reasoning:blackboxai/openai/gpt-5.3-codex}")
    private String modelReasoning;

    @Value("${ai.model.knowledge:blackboxai/google/gemini-3.1-pro-preview}")
    private String modelKnowledge;

    @Value("${ai.model.image:blackboxai/google/imagen-4-ultra}")
    private String modelImage;

    private static final String ROUTER_SYSTEM_PROMPT = """
            Role: You are the high-level AI Router for the Fruvia Chat system. \
            Your job is to analyse the user's message and decide which specialised model and task type to use.

            Available models and task types:

            MODEL_CHAT        -> For: general conversation, emotional support, writing assistance, concept explanation, greetings.
            MODEL_REASONING   -> For: hard math, complex programming, multi-step logical reasoning, code debugging.
            MODEL_KNOWLEDGE   -> For: summarising long chat history, reading attached documents, encyclopaedic knowledge lookup, long-form summarisation.
            MODEL_IMAGE       -> For: any image generation request, logo design, UI sketch, drawing, illustration.

            Process:
            Step 1: Scan the user's keywords and intent.
            Step 2: Classify into exactly one of the four task types above.
            Step 3: Return a single JSON object — no explanation, no markdown, pure JSON only.

            Required output format:
            {"selected_model":"<model_name>","task_type":"CORE_CHAT|REASONING_CODE|KNOWLEDGE|IMAGE_GEN","reason":"<short reason>","refined_prompt":"<optimised prompt>"}

            Rules for refined_prompt:
            - IMAGE_GEN: MUST be in English, describe the image in detail (subject, style, colour, composition, resolution).
            - CORE_CHAT: keep the user's original language.
            - REASONING_CODE and KNOWLEDGE: optimise the prompt for the specialist model.

            IMPORTANT: Return EXACTLY one JSON object, NO markdown code block, NO extra text.
            """;

    /**
     * Route a user message to the appropriate model.
     * Returns null if routing fails (caller should use default model).
     */
    public AiRouterResult route(String userMessage, String language) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", ROUTER_SYSTEM_PROMPT));
            messages.add(Map.of("role", "user", "content", userMessage));

            AiCompletionResult result = blackboxAiClient.complete(messages, routerModel, 300);
            String raw = result.getContent();

            if (!StringUtils.hasText(raw)) {
                log.warn("[AI Router] Empty response from router model");
                return null;
            }

            // Strip markdown code fences if present
            raw = raw.trim();
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }

            JsonNode node = objectMapper.readTree(raw);
            String selectedModel = node.path("selected_model").asText(null);
            String taskType = node.path("task_type").asText(null);
            String reason = node.path("reason").asText(null);
            String refinedPrompt = node.path("refined_prompt").asText(null);

            if (!StringUtils.hasText(taskType)) {
                log.warn("[AI Router] Missing task_type in response: {}", raw);
                return null;
            }

            // Map model names to actual Blackbox model IDs
            String resolvedModel = resolveModelId(taskType, selectedModel);

            log.info("[AI Router] task={}, model={}, reason={}", taskType, resolvedModel, reason);

            return AiRouterResult.builder()
                    .selectedModel(resolvedModel)
                    .taskType(taskType)
                    .reason(reason)
                    .refinedPrompt(StringUtils.hasText(refinedPrompt) ? refinedPrompt : userMessage)
                    .build();

        } catch (Exception ex) {
            log.warn("[AI Router] Routing failed, will use default model: {}", ex.getMessage());
            return null;
        }
    }

    private String resolveModelId(String taskType, String selectedModelHint) {
        return switch (taskType) {
            case AiRouterResult.TASK_CORE_CHAT -> modelChat;
            case AiRouterResult.TASK_REASONING_CODE -> modelReasoning;
            case AiRouterResult.TASK_KNOWLEDGE -> modelKnowledge;
            case AiRouterResult.TASK_IMAGE_GEN -> modelImage;
            default -> modelChat;
        };
    }

    public String getModelImage() {
        return modelImage;
    }
}
