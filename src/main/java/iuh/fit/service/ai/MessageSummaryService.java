package iuh.fit.service.ai;

import iuh.fit.dto.response.ai.SummarizeResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Message;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageSummaryService {

    private final MessageRepository messageRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserDetailRepository userDetailRepository;
    private final BlackboxAiClient aiClient;

    @Value("${ai.model.knowledge:blackboxai/google/gemini-3.1-pro-preview}")
    private String knowledgeModel;

    private static final int MAX_MESSAGES_PER_CHUNK = 200;
    private static final int MAX_TOTAL_MESSAGES = 1000;

    private static final String SUMMARY_SYSTEM_PROMPT = """
            Bạn là một thư ký chuyên nghiệp. Đây là danh sách tin nhắn trong một nhóm chat mà người dùng đã bỏ lỡ. \
            Hãy tóm tắt lại nội dung chính theo các mục sau:

            **Chủ đề chính:** (Tóm tắt trong 1-2 câu)

            **Các quyết định quan trọng:** (Nếu có)

            **Các đầu việc/Lịch hẹn:** (Ai làm gì, khi nào — nếu có)

            **Không khí cuộc trò chuyện:** (Vui vẻ, căng thẳng, hay bình thường)

            Nếu số tin nhắn ít (dưới 5), chỉ cần tóm tắt ngắn gọn 1-2 câu. Trả lời bằng ngôn ngữ của tin nhắn.""";

    private static final String REDUCE_SYSTEM_PROMPT = """
            Bạn là một thư ký chuyên nghiệp. Dưới đây là các bản tóm tắt từng phần của một cuộc hội thoại dài. \
            Hãy tổng hợp tất cả thành MỘT bản tóm tắt duy nhất theo format:

            **Chủ đề chính:** (Tóm tắt trong 1-2 câu)

            **Các quyết định quan trọng:** (Nếu có)

            **Các đầu việc/Lịch hẹn:** (Ai làm gì, khi nào — nếu có)

            **Không khí cuộc trò chuyện:** (Vui vẻ, căng thẳng, hay bình thường)

            Trả lời bằng ngôn ngữ của các bản tóm tắt.""";

    /**
     * Summarize the N most recent messages in a conversation (regardless of read
     * status).
     */
    public SummarizeResponse summarizeRecent(String conversationId, String userId, int messageCount) {
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Ban khong phai thanh vien cua cuoc hoi thoai nay"));

        long start = System.currentTimeMillis();
        int count = Math.min(messageCount, MAX_TOTAL_MESSAGES);

        Page<Message> page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, count));
        List<Message> messages = new ArrayList<>(page.getContent());
        Collections.reverse(messages);

        if (messages.isEmpty()) {
            return SummarizeResponse.builder()
                    .summary("Khong co tin nhan de tom tat.")
                    .messageCount(0)
                    .model("none")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        Map<String, String> senderNameCache = new HashMap<>();
        String summary;
        String model;

        if (messages.size() <= MAX_MESSAGES_PER_CHUNK) {
            String messagesText = formatMessages(messages, senderNameCache);
            AiCompletionResult result = callAiSummarize(SUMMARY_SYSTEM_PROMPT, messagesText);
            summary = result.getContent();
            model = result.getModel();
        } else {
            summary = mapReduceSummarize(messages, senderNameCache);
            model = knowledgeModel + " (map-reduce)";
        }

        return SummarizeResponse.builder()
                .summary(summary)
                .messageCount(messages.size())
                .model(model)
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * Summarize unread messages in a conversation for the given user.
     * Uses Map-Reduce pattern for large message counts.
     */
    public SummarizeResponse summarize(String conversationId, String userId, String lastReadMessageId) {
        // Verify membership
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không phải thành viên của cuộc hội thoại này"));

        long start = System.currentTimeMillis();

        // Determine cutoff: use provided lastReadMessageId, or member's lastReadAt
        List<Message> unreadMessages = fetchUnreadMessages(conversationId, userId, lastReadMessageId, member);

        if (unreadMessages.isEmpty()) {
            return SummarizeResponse.builder()
                    .summary("Không có tin nhắn mới cần tóm tắt.")
                    .messageCount(0)
                    .model("none")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // Build sender name cache to avoid repeated DB lookups
        Map<String, String> senderNameCache = new HashMap<>();

        String summary;
        String model;

        if (unreadMessages.size() <= MAX_MESSAGES_PER_CHUNK) {
            // Direct summarization — fits in one call
            String messagesText = formatMessages(unreadMessages, senderNameCache);
            AiCompletionResult result = callAiSummarize(SUMMARY_SYSTEM_PROMPT, messagesText);
            summary = result.getContent();
            model = result.getModel();
        } else {
            // Map-Reduce: split into chunks, summarize each, then reduce
            summary = mapReduceSummarize(unreadMessages, senderNameCache);
            model = knowledgeModel + " (map-reduce)";
        }

        long latency = System.currentTimeMillis() - start;

        return SummarizeResponse.builder()
                .summary(summary)
                .messageCount(unreadMessages.size())
                .model(model)
                .latencyMs(latency)
                .build();
    }

    private List<Message> fetchUnreadMessages(String conversationId, String userId,
            String lastReadMessageId, ConversationMember member) {
        // If a specific lastReadMessageId is provided, find messages after it
        if (lastReadMessageId != null && !lastReadMessageId.isBlank()) {
            Message lastRead = messageRepository.findById(lastReadMessageId).orElse(null);
            if (lastRead != null && lastRead.getCreatedAt() != null) {
                return fetchMessagesAfter(conversationId, userId, lastRead.getCreatedAt());
            }
        }

        // Fallback: use member's lastReadAt
        if (member.getLastReadAt() != null) {
            return fetchMessagesAfter(conversationId, userId, member.getLastReadAt());
        }

        // Never read — get most recent MAX_TOTAL_MESSAGES
        Page<Message> page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, MAX_TOTAL_MESSAGES));
        List<Message> result = new ArrayList<>(page.getContent());
        Collections.reverse(result);
        return result;
    }

    private List<Message> fetchMessagesAfter(String conversationId, String userId, LocalDateTime after) {
        // Fetch in pages of 200, up to MAX_TOTAL_MESSAGES
        List<Message> allMessages = new ArrayList<>();
        int pageNum = 0;
        while (allMessages.size() < MAX_TOTAL_MESSAGES) {
            Page<Message> page = messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    conversationId, LocalDateTime.now(), PageRequest.of(pageNum, 200));
            if (page.isEmpty())
                break;

            for (Message msg : page.getContent()) {
                if (msg.getCreatedAt() != null && msg.getCreatedAt().isAfter(after)
                        && !userId.equals(msg.getSenderId())) {
                    allMessages.add(msg);
                }
            }

            if (page.isLast())
                break;
            pageNum++;
        }

        // Sort by createdAt ASC
        allMessages.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null)
                return 0;
            return a.getCreatedAt().compareTo(b.getCreatedAt());
        });

        // Limit
        if (allMessages.size() > MAX_TOTAL_MESSAGES) {
            allMessages = allMessages.subList(allMessages.size() - MAX_TOTAL_MESSAGES, allMessages.size());
        }

        return allMessages;
    }

    /**
     * Map-Reduce summarization for large message sets.
     * Step 1 (Map): Split into chunks, summarize each chunk individually.
     * Step 2 (Reduce): Combine all chunk summaries into one final summary.
     */
    private String mapReduceSummarize(List<Message> messages, Map<String, String> senderNameCache) {
        // Map phase: chunk and summarize
        List<String> chunkSummaries = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += MAX_MESSAGES_PER_CHUNK) {
            int end = Math.min(i + MAX_MESSAGES_PER_CHUNK, messages.size());
            List<Message> chunk = messages.subList(i, end);
            String chunkText = formatMessages(chunk, senderNameCache);

            try {
                AiCompletionResult result = callAiSummarize(SUMMARY_SYSTEM_PROMPT, chunkText);
                chunkSummaries.add(result.getContent());
            } catch (Exception e) {
                log.error("Failed to summarize chunk {}-{}: {}", i, end, e.getMessage());
                chunkSummaries.add("[Không thể tóm tắt phần này]");
            }
        }

        if (chunkSummaries.size() == 1) {
            return chunkSummaries.get(0);
        }

        // Reduce phase: merge all chunk summaries
        String combined = chunkSummaries.stream()
                .map(s -> "--- Phần tóm tắt ---\n" + s)
                .collect(Collectors.joining("\n\n"));

        try {
            AiCompletionResult reduceResult = callAiSummarize(REDUCE_SYSTEM_PROMPT, combined);
            return reduceResult.getContent();
        } catch (Exception e) {
            log.error("Map-Reduce reduce phase failed: {}", e.getMessage());
            return String.join("\n\n", chunkSummaries);
        }
    }

    private AiCompletionResult callAiSummarize(String systemPrompt, String content) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "Danh sách tin nhắn:\n" + content));

        return aiClient.complete(messages, knowledgeModel, 1500);
    }

    private String formatMessages(List<Message> messages, Map<String, String> senderNameCache) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg.getIsDeleted() != null && msg.getIsDeleted())
                continue;
            if (msg.getIsRecalled() != null && msg.getIsRecalled())
                continue;
            if (msg.getContent() == null || msg.getContent().isBlank())
                continue;

            String senderName = senderNameCache.computeIfAbsent(
                    msg.getSenderId(), this::resolveSenderName);

            String type = msg.getMessageType() != null ? msg.getMessageType().name() : null;
            String content;
            if ("TEXT".equals(type)) {
                content = msg.getContent();
            } else if ("IMAGE".equals(type)) {
                content = "[Hình ảnh]";
            } else if ("VIDEO".equals(type)) {
                content = "[Video]";
            } else if ("VOICE".equals(type)) {
                content = "[Tin nhắn thoại]";
            } else if ("MEDIA".equals(type)) {
                content = "[File đính kèm]";
            } else {
                content = msg.getContent();
            }

            sb.append(senderName).append(": ").append(content).append("\n");
        }
        return sb.toString();
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
