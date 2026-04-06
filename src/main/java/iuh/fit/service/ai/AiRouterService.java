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

    private final BlackboxAiClient blackboxAiClient;
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
            Role: Bạn là Bộ não điều phối (AI Router) cấp cao cho hệ thống Fruvia Chat. \
            Nhiệm vụ của bạn là phân tích tin nhắn của người dùng và quyết định xem Task này thuộc loại nào \
            để gọi Model chuyên biệt tương ứng.

            Danh sách Model & Task quy định:

            MODEL_CHAT -> Dùng cho: Trò chuyện thông thường, tư vấn tâm lý, viết lách, giải thích khái niệm tự nhiên, chào hỏi.
            MODEL_REASONING -> Dùng cho: Giải toán khó, lập trình phức tạp, lỗi logic nặng, yêu cầu suy luận đa bước, debug code.
            MODEL_KNOWLEDGE -> Dùng cho: Tóm tắt lịch sử chat dài, đọc file tài liệu đính kèm, tra cứu kiến thức bách khoa, tóm tắt nội dung dài.
            MODEL_IMAGE -> Dùng cho: Tất cả yêu cầu tạo hình ảnh, vẽ logo, phác thảo UI, tạo ảnh, vẽ tranh, minh họa.

            Quy trình:
            Bước 1: Quét từ khóa và ý định (Intent) của người dùng.
            Bước 2: Phân loại vào 1 trong 4 Task trên.
            Bước 3: Trả về kết quả JSON (không giải thích thêm, không markdown, chỉ JSON thuần).

            Cấu trúc đầu ra JSON bắt buộc:
            {"selected_model":"Tên model","task_type":"CORE_CHAT|REASONING_CODE|KNOWLEDGE|IMAGE_GEN","reason":"Lý do ngắn","refined_prompt":"Prompt tối ưu hóa"}

            Quy tắc refined_prompt:
            - Với IMAGE_GEN: refined_prompt PHẢI bằng tiếng Anh, mô tả chi tiết hình ảnh cần tạo (subject, style, color, composition, resolution).
            - Với CORE_CHAT: giữ nguyên ngôn ngữ gốc của người dùng.
            - Với REASONING_CODE và KNOWLEDGE: tối ưu prompt cho model chuyên biệt.

            QUAN TRỌNG: Trả về ĐÚNG 1 JSON object, KHÔNG có markdown code block, KHÔNG có text thừa.
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
