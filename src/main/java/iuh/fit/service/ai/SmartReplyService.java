package iuh.fit.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.dto.response.ai.SmartReplyResponse;
import iuh.fit.entity.Message;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartReplyService {

    private final MessageRepository messageRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserDetailRepository userDetailRepository;
    private final BlackboxAiClient aiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.model.chat:blackboxai/blackbox-pro}")
    private String chatModel;

    private static final int CONTEXT_SIZE = 10;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String CACHE_KEY_PREFIX = "ai:smart-reply:";

    private static final String SYSTEM_PROMPT = """
            Bạn là một trợ lý chat thông minh. Dựa trên lịch sử hội thoại dưới đây, \
            hãy gợi ý 3 câu trả lời ngắn gọn, tự nhiên và phù hợp với ngữ cảnh nhất cho người dùng.

            Trả về kết quả dưới dạng JSON array: ["option 1", "option 2", "option 3"].

            Câu trả lời không quá 10 từ.

            Ngôn ngữ: Tương ứng với ngôn ngữ của hội thoại (Tiếng Việt/Tiếng Anh).

            CHỈ trả về JSON array, KHÔNG giải thích gì thêm.""";

    /**
     * Generate 3 smart reply suggestions for the given conversation.
     * Results are cached in Redis for 60 seconds to avoid redundant AI calls.
     */
    public SmartReplyResponse generateSmartReplies(String conversationId, String userId) {
        // Verify user is member of conversation
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không phải thành viên của cuộc hội thoại này"));

        // Check Redis cache
        String cacheKey = CACHE_KEY_PREFIX + conversationId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<String> suggestions = objectMapper.readValue(cached, new TypeReference<>() {
                });
                return SmartReplyResponse.builder()
                        .suggestions(suggestions)
                        .model("cache")
                        .latencyMs(0)
                        .build();
            }
        } catch (Exception e) {
            log.debug("Smart reply cache miss or error: {}", e.getMessage());
        }

        // Fetch recent messages for context
        var page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, CONTEXT_SIZE));
        List<Message> recentMessages = new ArrayList<>(page.getContent());
        Collections.reverse(recentMessages); // oldest first

        if (recentMessages.isEmpty()) {
            return SmartReplyResponse.builder()
                    .suggestions(List.of("Xin chào! 👋", "Bạn có khỏe không?", "Rất vui được gặp bạn!"))
                    .model("default")
                    .latencyMs(0)
                    .build();
        }

        // Build conversation history string
        StringBuilder history = new StringBuilder();
        for (Message msg : recentMessages) {
            if (msg.getIsDeleted() != null && msg.getIsDeleted())
                continue;
            if (msg.getIsRecalled() != null && msg.getIsRecalled())
                continue;
            if (!"TEXT".equals(msg.getMessageType()))
                continue;

            String senderName = resolveSenderName(msg.getSenderId());
            String role = msg.getSenderId().equals(userId) ? "Tôi" : senderName;
            history.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        if (history.isEmpty()) {
            return SmartReplyResponse.builder()
                    .suggestions(List.of("Xin chào! 👋", "Bạn có khỏe không?", "Rất vui được gặp bạn!"))
                    .model("default")
                    .latencyMs(0)
                    .build();
        }

        // Call AI
        long start = System.currentTimeMillis();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", "Lịch sử hội thoại:\n" + history));

        try {
            AiCompletionResult result = aiClient.complete(messages, chatModel, 200);
            long latency = System.currentTimeMillis() - start;

            List<String> suggestions = parseJsonArray(result.getContent());
            if (suggestions.size() < 3) {
                suggestions = List.of("Ok 👍", "Để mình xem lại nhé", "Mình hiểu rồi");
            }

            // Cache for 60 seconds
            try {
                stringRedisTemplate.opsForValue().set(
                        cacheKey, objectMapper.writeValueAsString(suggestions), CACHE_TTL);
            } catch (Exception e) {
                log.debug("Failed to cache smart reply: {}", e.getMessage());
            }

            return SmartReplyResponse.builder()
                    .suggestions(suggestions)
                    .model(result.getModel())
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            log.error("Smart reply AI call failed: {}", e.getMessage());
            return SmartReplyResponse.builder()
                    .suggestions(List.of("Ok 👍", "Để mình xem lại nhé", "Mình hiểu rồi"))
                    .model("fallback")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private List<String> parseJsonArray(String content) {
        try {
            // Extract JSON array from content (may contain extra text)
            String trimmed = content.trim();
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String json = trimmed.substring(start, end + 1);
                return objectMapper.readValue(json, new TypeReference<>() {
                });
            }
        } catch (Exception e) {
            log.warn("Failed to parse smart reply JSON: {}", e.getMessage());
        }
        return List.of();
    }

    private String resolveSenderName(String senderId) {
        try {
            return userDetailRepository.findByUserId(senderId)
                    .map(ud -> ud.getDisplayName() != null ? ud.getDisplayName() : "User")
                    .orElse("User");
        } catch (Exception e) {
            return "User";
        }
    }
}
