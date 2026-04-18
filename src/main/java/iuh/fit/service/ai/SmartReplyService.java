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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    private static final int CONTEXT_SIZE = 20;
    private static final Duration CACHE_TTL = Duration.ofSeconds(120);
    private static final String CACHE_KEY_PREFIX = "ai:smart-reply:";

    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}" +
                    "\\x{1F700}-\\x{1F77F}\\x{1F780}-\\x{1F7FF}\\x{2600}-\\x{26FF}" +
                    "\\x{2700}-\\x{27BF}\\x{FE00}-\\x{FE0F}\\x{1F900}-\\x{1F9FF}]",
            Pattern.UNICODE_CHARACTER_CLASS);

    public SmartReplyResponse generateSmartReplies(String conversationId, String userId) {
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Ban khong phai thanh vien cua cuoc hoi thoai nay"));

        var page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, CONTEXT_SIZE));
        List<Message> recentMessages = new ArrayList<>(page.getContent());
        Collections.reverse(recentMessages);

        String lastMsgId = recentMessages.isEmpty() ? "empty"
                : recentMessages.get(recentMessages.size() - 1).getMessageId();
        String cacheKey = CACHE_KEY_PREFIX + conversationId + ":" + lastMsgId;

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

        String userName = userDetailRepository.findByUserId(userId)
                .map(ud -> ud.getDisplayName() != null ? ud.getDisplayName() : "Toi")
                .orElse("Toi");

        StringBuilder history = new StringBuilder();
        List<String> myOwnMessages = new ArrayList<>();

        for (Message msg : recentMessages) {
            if (Boolean.TRUE.equals(msg.getIsDeleted()))
                continue;
            if (Boolean.TRUE.equals(msg.getIsRecalled()))
                continue;

            String type = msg.getMessageType() != null ? msg.getMessageType().name() : "TEXT";
            String content;
            if ("TEXT".equals(type)) {
                content = msg.getContent();
            } else if ("IMAGE".equals(type) || "MEDIA".equals(type)) {
                content = "[Anh/File dinh kem]";
            } else if ("VOICE".equals(type)) {
                content = "[Tin nhan thoai]";
            } else if ("STICKER".equals(type)) {
                content = "[Sticker]";
            } else {
                content = "[" + type + "]";
            }

            boolean isMe = userId.equals(msg.getSenderId());
            String label = isMe ? userName : resolveSenderName(msg.getSenderId());
            history.append(label).append(": ").append(content).append("\n");

            if (isMe && "TEXT".equals(type) && content != null && !content.isBlank()) {
                myOwnMessages.add(content);
            }
        }

        if (history.isEmpty()) {
            return defaultReply();
        }

        String styleHint = analyzeWritingStyle(myOwnMessages);

        String lastNonEmptyContent = "";
        String lastSender = "";
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            Message msg = recentMessages.get(i);
            if (Boolean.TRUE.equals(msg.getIsDeleted()) || Boolean.TRUE.equals(msg.getIsRecalled()))
                continue;
            if (msg.getMessageType() == null || !"TEXT".equals(msg.getMessageType().name()))
                continue;
            lastNonEmptyContent = msg.getContent();
            lastSender = userId.equals(msg.getSenderId()) ? userName : resolveSenderName(msg.getSenderId());
            break;
        }

        String taskContext;
        if (!lastNonEmptyContent.isEmpty() && !lastSender.equals(userName)) {
            taskContext = "Tin nhan cuoi la cua " + lastSender + ": \"" + lastNonEmptyContent
                    + "\"\n" + userName + " can tra loi tin nhan do.";
        } else if (!lastNonEmptyContent.isEmpty()) {
            taskContext = "Tin nhan cuoi la cua chinh " + userName + ": \"" + lastNonEmptyContent
                    + "\"\nChua ai tra loi. Goi y cach " + userName
                    + " co the nhac, hoi lai, hoac noi them de doi phuong rep (vd: 'alo rep di', 'thay chua?', 'hello?', ...).";
        } else {
            taskContext = "Goi y nhung gi " + userName + " co the nhan tiep theo.";
        }

        String systemPrompt = buildSystemPrompt(userName, styleHint, taskContext);

        long start = System.currentTimeMillis();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content",
                "=== LICH SU HOI THOAI (20 tin nhan gan nhat) ===\n" + history.toString().trim()
                        + "\n\n" + taskContext
                        + "\n\nTra ve dung 3 goi y dang JSON array."));

        try {
            AiCompletionResult result = aiClient.complete(messages, chatModel, 200);
            long latency = System.currentTimeMillis() - start;

            List<String> suggestions = parseJsonArray(result.getContent());
            if (suggestions.size() < 2) {
                suggestions = List.of("Ok dc", "Uh r", "Oke nha");
            }
            while (suggestions.size() < 3)
                suggestions.add("...");
            suggestions = suggestions.subList(0, 3);

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
                    .suggestions(List.of("Ok dc", "Uh de t xem", "Oke"))
                    .model("fallback")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private String buildSystemPrompt(String userName, String styleHint, String taskContext) {
        return "Ban la mot nguoi ban than dang goi y tra loi chat cho " + userName
                + ". De xuat dung 3 cau ma " + userName + " co the gui tiep.\n\n"
                + "PHONG CACH NHAN TIN CUA " + userName + ":\n"
                + styleHint + "\n\n"
                + "QUY TAC BAT BUOC:\n"
                + "1. NOI NHU NGUOI THAT - dung viet tat (ok, dc, ko, oke, r, k, nha, uh, tui, mk, ...), teencode, tieng long neu phong cach ho nhu vay.\n"
                + "2. KHONG THAO MAI - cam noi kieu 'Rat vui duoc gap ban', 'Cam on ban nhieu', 'Chuc ban mot ngay tot lanh'. Day la chat ban be, KHONG PHAI email cong ty.\n"
                + "3. Co the hoi tuc, noi bua, treu choc, reaction manh - NHU NGUOI THAT CHAT VOI BAN THAN.\n"
                + "4. Bat chuoc chinh xac phong cach - neu ho dung emoji thi goi y co emoji, neu nhan ngan 1-3 tu thi goi y cung ngan.\n"
                + "5. Moi goi y toi da 12 tu. Ngan hon = tot hon.\n"
                + "6. Dung chu de dang noi - KHONG doi chu de.\n"
                + "7. Ngon ngu khop voi hoi thoai (Viet/Anh/mix).\n"
                + "8. NEU tin nhan cuoi la cua chinh " + userName
                + " (chua ai tra loi), thi goi y: nhac lai, hoi them, hoac noi gi do de doi phuong phan hoi (vd: 'alo?', 'rep di', 'thay chua?', ...).\n"
                + "9. CHI tra ve JSON array: [\"goi y 1\", \"goi y 2\", \"goi y 3\"]\n"
                + "10. KHONG giai thich, KHONG them text ngoai array.";
    }

    private String analyzeWritingStyle(List<String> myMessages) {
        if (myMessages.isEmpty()) {
            return "Chua co du lieu - dung phong cach tu nhien, than mat, ngan gon.";
        }

        double avgWords = myMessages.stream()
                .mapToInt(m -> m.trim().split("\\s+").length)
                .average()
                .orElse(5);

        String lengthStyle;
        if (avgWords <= 4)
            lengthStyle = "nhan rat ngan (1-4 tu)";
        else if (avgWords <= 10)
            lengthStyle = "nhan ngan vua (5-10 tu)";
        else
            lengthStyle = "nhan kha dai (>10 tu)";

        long emojiMsgCount = myMessages.stream()
                .filter(m -> EMOJI_PATTERN.matcher(m).find())
                .count();
        double emojiRatio = (double) emojiMsgCount / myMessages.size();
        String emojiStyle;
        if (emojiRatio > 0.5)
            emojiStyle = "dung emoji thuong xuyen";
        else if (emojiRatio > 0.15)
            emojiStyle = "doi khi dung emoji";
        else
            emojiStyle = "hiem khi hoac khong dung emoji";

        long casualCount = myMessages.stream()
                .filter(m -> {
                    String lower = m.toLowerCase();
                    return lower.contains("u") || lower.contains("thi") || lower.contains("ma")
                            || lower.contains("nha") || lower.contains("ne") || lower.contains("a")
                            || lower.contains("oke") || lower.contains("ok") || lower.contains("haha")
                            || lower.contains("hihi") || lower.contains("vay") || lower.contains("minh");
                })
                .count();
        double casualRatio = (double) casualCount / myMessages.size();
        String formalityStyle;
        if (casualRatio > 0.4)
            formalityStyle = "phong cach than mat, binh thuong nhu ban be";
        else if (casualRatio > 0.15)
            formalityStyle = "kha than thien, khong qua trang trong";
        else
            formalityStyle = "lich su / trang trong";

        return "- Do dai: " + lengthStyle + "\n- Emoji: " + emojiStyle + "\n- Phong cach: " + formalityStyle;
    }

    private SmartReplyResponse defaultReply() {
        return SmartReplyResponse.builder()
                .suggestions(List.of("Yo!", "Alo co ai ko?", "Hi nha"))
                .model("default")
                .latencyMs(0)
                .build();
    }

    private List<String> parseJsonArray(String content) {
        try {
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