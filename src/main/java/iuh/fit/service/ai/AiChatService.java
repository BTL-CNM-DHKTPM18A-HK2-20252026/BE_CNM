package iuh.fit.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.document.MessageDocument;
import iuh.fit.dto.request.message.ChatWithAiRequest;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.dto.response.message.AiChatResponse;
import iuh.fit.dto.response.message.MessageAndConversationResponse;
import iuh.fit.dto.response.message.MessageResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.FileUpload;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.Message;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.AiMessageStatus;
import iuh.fit.enums.AiRole;
import iuh.fit.enums.ConversationStatus;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.MemberRole;
import iuh.fit.enums.MessageType;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.mapper.MessageMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.FileUploadRepository;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.response.SearchResult;
import iuh.fit.service.search.SearchService;
import iuh.fit.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private static final String AI_SENDER_ID = "FRUVIA_AI_ASSISTANT";
    private static final String AI_CONVERSATION_NAME = "Fruvia Chatbot";
    private static final Pattern DIRECT_IMAGE_COMMAND_PATTERN = Pattern
            .compile("^\\s*[/／⁄∕](image|image_pro|sketch|wallpaper)\\b(.*)$", Pattern.CASE_INSENSITIVE);
    private static final int CONTINUATION_MAX_ROUNDS = 2;
    private static final int CONTINUATION_MAX_TOKENS = 320;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserAuthRepository userAuthRepository;
    private final UserDetailRepository userDetailRepository;
    private final UserSettingRepository userSettingRepository;
    private final FriendshipRepository friendshipRepository;
    private final FileUploadRepository fileUploadRepository;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final SimpMessageSendingOperations messagingTemplate;
    private final SearchService searchService;
    private final StorageService storageService;
    private final BlackboxAiClient blackboxAiClient;
    private final AiImageWorkflowService aiImageWorkflowService;
    private final AiSystemPromptLibrary systemPromptLibrary;
    private final AiRouterService aiRouterService;
    private final ObjectMapper objectMapper;

    @Value("${ai.sliding-window.size:10}")
    private int slidingWindowSize;

    @Value("${ai.window-token-budget:2000}")
    private int windowTokenBudget;

    @Value("${ai.max-retries:2}")
    private int maxRetries;

    @Value("${BLACKBOX_MODEL:blackboxai/blackbox-pro}")
    private String defaultModel;

    @Transactional
    public ConversationResponse ensureAiConversation(String userId) {
        Conversations conversation = resolveConversation(userId, null, LocalDateTime.now());
        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(conversation.getConversationId());
        return conversationMapper.toResponse(conversation, members, userId);
    }

    @Transactional
    public AiChatResponse chatWithAi(String userId, ChatWithAiRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Conversations conversation = resolveConversation(userId, request.getConversationId(), now);

        Message userMessage = createUserMessage(conversation.getConversationId(), userId, request.getContent(), now);
        updateConversationLastMessage(conversation, userMessage);

        String senderName = userDetailRepository.findByUserId(userId)
                .map(UserDetail::getDisplayName)
                .orElse("Unknown");
        searchService.indexMessage(userMessage, senderName);

        broadcastAiTyping(conversation.getConversationId(), true);
        try {
            // --- Fast-path: explicit /image commands bypass router ---
            QuickCommand imageCommand = parseImageGenerationCommand(request.getContent());
            if (imageCommand != null) {
                return handleImageGenerationRequest(userId, request, conversation, userMessage, imageCommand);
            }

            // --- Step 1: AI Router – classify intent and select model ---
            String language = resolveResponseLanguage(request.getLanguage());
            AiRouterResult routerResult = aiRouterService.route(request.getContent(), language);

            // If router detected IMAGE_GEN task, redirect to image workflow
            if (routerResult != null && AiRouterResult.TASK_IMAGE_GEN.equals(routerResult.getTaskType())) {
                QuickCommand routerImageCommand = new QuickCommand("image.generate",
                        routerResult.getRefinedPrompt());
                return handleImageGenerationRequest(userId, request, conversation, userMessage, routerImageCommand);
            }

            // --- Step 2: Build context & call the routed model ---
            String selectedModel = (routerResult != null) ? routerResult.getSelectedModel() : defaultModel;

            List<Message> recentMessages = messageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversation.getConversationId(), PageRequest.of(0, 30))
                    .getContent();

            List<Message> slidingWindow = buildSlidingWindow(recentMessages);
            String summary = updateSummaryIfNeeded(conversation, recentMessages, slidingWindow);
            List<String> ragContexts = getRagContexts(conversation.getConversationId(), request);
            boolean fullAccessGranted = Boolean.TRUE.equals(request.getFullAccessGranted());
            String fullAccessContext = fullAccessGranted
                    ? buildFullAccessContext(userId, conversation.getConversationId(), request.getLanguage())
                    : null;
            String userProfileContext = fullAccessGranted ? null
                    : buildUserProfileContext(userId, request.getLanguage());
            String storageContext = fullAccessGranted
                    ? null
                    : buildStorageContextIfRequested(userId, request.getContent(), request.getLanguage());
            List<Map<String, String>> promptMessages = buildPrompt(summary, ragContexts, slidingWindow,
                    request.getLanguage(), request.getThemeType(), userProfileContext, storageContext,
                    fullAccessContext);

            // If router provided a refined prompt, replace the last user message in the
            // prompt
            if (routerResult != null && StringUtils.hasText(routerResult.getRefinedPrompt())
                    && !AiRouterResult.TASK_CORE_CHAT.equals(routerResult.getTaskType())) {
                replaceLastUserMessage(promptMessages, routerResult.getRefinedPrompt());
            }

            AiCompletionResult completion = null;
            boolean fallbackUsed = false;
            String providerStatus = "COMPLETED";
            String errorCode = null;
            String errorMessage = null;

            long startedAt = System.currentTimeMillis();
            for (int attempt = 1; attempt <= Math.max(1, maxRetries + 1); attempt++) {
                try {
                    completion = blackboxAiClient.complete(promptMessages, selectedModel);
                    break;
                } catch (Exception ex) {
                    boolean timeout = blackboxAiClient.isTimeout(ex);
                    boolean canRetry = attempt <= maxRetries;
                    log.warn("AI call failed attempt {}/{} (model={}): {}", attempt, maxRetries + 1, selectedModel,
                            ex.getMessage());

                    // On last attempt with a routed model, fallback to default model
                    if (!canRetry && routerResult != null && !selectedModel.equals(defaultModel)) {
                        log.info("[AI Router] Routed model {} failed, falling back to default {}", selectedModel,
                                defaultModel);
                        try {
                            completion = blackboxAiClient.complete(promptMessages, defaultModel);
                            fallbackUsed = true;
                            selectedModel = defaultModel;
                            break;
                        } catch (Exception fallbackEx) {
                            log.warn("Default model fallback also failed: {}", fallbackEx.getMessage());
                        }
                    }

                    if (!canRetry) {
                        fallbackUsed = true;
                        providerStatus = timeout ? "TIMEOUT" : "FAILED";
                        errorCode = timeout ? "AI_TIMEOUT" : "AI_PROVIDER_ERROR";
                        errorMessage = ex.getMessage();
                    }
                }
            }

            if (completion != null) {
                completion = completeInterruptedResponse(promptMessages, completion, selectedModel, language);
            }

            long latency = System.currentTimeMillis() - startedAt;

            String assistantContent;
            AiMessageStatus aiStatus;
            int promptTokens;
            int completionTokens;
            int totalTokens;
            String requestId;

            if (completion != null) {
                assistantContent = completion.getContent();
                aiStatus = AiMessageStatus.COMPLETED;
                promptTokens = completion.getPromptTokens() > 0 ? completion.getPromptTokens()
                        : estimatePromptTokenCount(promptMessages);
                completionTokens = completion.getCompletionTokens() > 0 ? completion.getCompletionTokens()
                        : estimateTokenCount(completion.getContent());
                totalTokens = completion.getTotalTokens() > 0 ? completion.getTotalTokens()
                        : promptTokens + completionTokens;
                requestId = completion.getProviderRequestId();
            } else {
                assistantContent = "Xin lỗi, AI đang bận hoặc quá thời gian phản hồi. Bạn thử lại sau vài giây nhé.";
                aiStatus = "TIMEOUT".equals(providerStatus) ? AiMessageStatus.TIMEOUT : AiMessageStatus.FAILED;
                promptTokens = estimatePromptTokenCount(promptMessages);
                completionTokens = estimateTokenCount(assistantContent);
                totalTokens = promptTokens + completionTokens;
                requestId = UUID.randomUUID().toString();
            }

            String routerInfo = routerResult != null
                    ? String.format("[%s] %s", routerResult.getTaskType(), routerResult.getReason())
                    : null;

            Message assistantMessage = Message.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(conversation.getConversationId())
                    .senderId(AI_SENDER_ID)
                    .role(AiRole.ASSISTANT)
                    .messageType(MessageType.TEXT)
                    .content(assistantContent)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .isDeleted(false)
                    .isRecalled(false)
                    .isEdited(false)
                    .aiGenerated(true)
                    .aiModel(selectedModel)
                    .aiStatus(aiStatus)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .aiRequestId(requestId)
                    .aiLatencyMs(latency)
                    .aiErrorCode(errorCode)
                    .aiErrorMessage(errorMessage)
                    .build();

            assistantMessage = messageRepository.save(assistantMessage);
            if (completion != null) {
                searchService.indexMessage(assistantMessage, "Fruvia Chatbot");
            }

            updateConversationLastMessage(conversation, assistantMessage);

            List<ConversationMember> members = conversationMemberRepository
                    .findByConversationId(conversation.getConversationId());
            ConversationResponse conversationResponse = conversationMapper.toResponse(conversation, members, userId);

            MessageResponse userMessageResponse = messageMapper.toResponse(userMessage);
            MessageResponse assistantMessageResponse = messageMapper.toResponse(assistantMessage);

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversation.getConversationId(),
                    MessageAndConversationResponse.builder()
                            .message(assistantMessageResponse)
                            .conversation(conversationResponse)
                            .build());

            return AiChatResponse.builder()
                    .userMessage(userMessageResponse)
                    .imageMessage(null)
                    .assistantMessage(assistantMessageResponse)
                    .conversation(conversationResponse)
                    .fallbackUsed(fallbackUsed)
                    .providerStatus(providerStatus)
                    .build();
        } finally {
            broadcastAiTyping(conversation.getConversationId(), false);
        }
    }

    /**
     * Replace the last user message in the prompt list with the router's refined
     * prompt.
     */
    private void replaceLastUserMessage(List<Map<String, String>> promptMessages, String refinedPrompt) {
        for (int i = promptMessages.size() - 1; i >= 0; i--) {
            if ("user".equals(promptMessages.get(i).get("role"))) {
                promptMessages.set(i, createPromptMessage("user", refinedPrompt));
                return;
            }
        }
    }

    private AiCompletionResult completeInterruptedResponse(
            List<Map<String, String>> basePromptMessages,
            AiCompletionResult initialCompletion,
            String selectedModel,
            String language) {
        if (initialCompletion == null || !StringUtils.hasText(initialCompletion.getContent())) {
            return initialCompletion;
        }

        String mergedContent = initialCompletion.getContent().trim();
        int promptTokens = Math.max(0, initialCompletion.getPromptTokens());
        int completionTokens = Math.max(0, initialCompletion.getCompletionTokens());
        int totalTokens = Math.max(0, initialCompletion.getTotalTokens());
        String finishReason = initialCompletion.getFinishReason();

        for (int round = 1; round <= CONTINUATION_MAX_ROUNDS; round++) {
            if (!shouldContinueResponse(mergedContent, finishReason)) {
                break;
            }

            List<Map<String, String>> continuationPrompt = new ArrayList<>(basePromptMessages);
            continuationPrompt.add(createPromptMessage("assistant", mergedContent));
            continuationPrompt.add(createPromptMessage("user", buildContinuationInstruction(language)));

            try {
                AiCompletionResult continuation = blackboxAiClient.complete(
                        continuationPrompt,
                        selectedModel,
                        CONTINUATION_MAX_TOKENS);

                if (continuation == null || !StringUtils.hasText(continuation.getContent())) {
                    break;
                }

                String addition = trimContinuationPrefix(continuation.getContent());
                if (!StringUtils.hasText(addition)) {
                    break;
                }

                mergedContent = mergeWithOverlap(mergedContent, addition);
                promptTokens += Math.max(0, continuation.getPromptTokens());
                completionTokens += Math.max(0, continuation.getCompletionTokens());
                totalTokens += Math.max(0, continuation.getTotalTokens());
                finishReason = continuation.getFinishReason();
            } catch (Exception ex) {
                log.warn("AI continuation failed at round {}: {}", round, ex.getMessage());
                break;
            }
        }

        int effectiveTotal = totalTokens > 0 ? totalTokens : promptTokens + completionTokens;
        return AiCompletionResult.builder()
                .content(mergedContent)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(effectiveTotal)
                .providerRequestId(initialCompletion.getProviderRequestId())
                .model(initialCompletion.getModel())
                .finishReason(finishReason)
                .build();
    }

    private boolean shouldContinueResponse(String content, String finishReason) {
        if (!StringUtils.hasText(content)) {
            return false;
        }

        if (isLikelyTruncatedByFinishReason(finishReason)) {
            return true;
        }

        String trimmed = content.trim();
        if (trimmed.length() < 24) {
            return false;
        }

        if (hasUnclosedPair(trimmed, '(', ')')
                || hasUnclosedPair(trimmed, '[', ']')
                || hasUnclosedPair(trimmed, '{', '}')) {
            return true;
        }

        if (countOccurrences(trimmed, "**") % 2 != 0 || countOccurrences(trimmed, "```") % 2 != 0) {
            return true;
        }

        if (looksLikeStandaloneUrl(trimmed)) {
            return false;
        }

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (",:;(-/".indexOf(lastChar) >= 0 || lastChar == '*' || lastChar == '#') {
            return true;
        }

        if (isLikelyIncompleteEnding(trimmed)) {
            return true;
        }

        String normalized = normalizeForMatch(trimmed);
        String[] tokens = normalized.split("\\s+");
        if (tokens.length == 0) {
            return false;
        }

        String lastToken = tokens[tokens.length - 1];
        return Set.of(
                "va", "hoac", "nhung", "vi", "tai", "muc", "gia", "xu", "huong",
                "and", "or", "but", "because", "with", "at", "around", "about", "trend")
                .contains(lastToken);
    }

    private boolean isLikelyTruncatedByFinishReason(String finishReason) {
        if (!StringUtils.hasText(finishReason)) {
            return false;
        }

        String normalized = finishReason.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("length")
                || normalized.contains("max_tokens")
                || normalized.contains("token_limit")
                || normalized.contains("max");
    }

    private boolean isLikelyIncompleteEnding(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        String trimmed = text.trim();
        if (trimmed.length() < 60) {
            return false;
        }

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (isNaturalTerminalChar(lastChar)) {
            return false;
        }

        // Avoid over-triggering on single token snippets (e.g. IDs/short labels)
        String normalized = normalizeForMatch(trimmed);
        String[] tokens = normalized.split("\\s+");
        if (tokens.length < 6) {
            return false;
        }

        // If the response already has some structure and ends without terminal
        // punctuation, it is likely truncated.
        return trimmed.contains("\n")
                || trimmed.contains(":")
                || countOccurrences(trimmed, ".") >= 1
                || countOccurrences(trimmed, "!") >= 1
                || countOccurrences(trimmed, "?") >= 1;
    }

    private boolean isNaturalTerminalChar(char c) {
        return ".!?;:…)]}>'\"`”’」。！？".indexOf(c) >= 0;
    }

    private boolean looksLikeStandaloneUrl(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.matches("(?i)^https?://\\S+$");
    }

    private boolean hasUnclosedPair(String text, char open, char close) {
        int balance = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == open) {
                balance++;
            } else if (c == close) {
                balance = Math.max(0, balance - 1);
            }
        }
        return balance > 0;
    }

    private int countOccurrences(String text, String token) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(token)) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private String buildContinuationInstruction(String language) {
        if ("vi".equals(resolveResponseLanguage(language))) {
            return "Cau tra loi truoc vua bi ngat giua chung. Hay viet tiep ngay tai vi tri dang do, khong lap lai noi dung da viet, va ket thuc tron y trong 1-2 cau.";
        }

        return "Your previous answer was cut off mid-sentence. Continue exactly from where it stopped, do not repeat previous text, and finish the thought naturally in 1-2 sentences.";
    }

    private String trimContinuationPrefix(String addition) {
        if (!StringUtils.hasText(addition)) {
            return addition;
        }

        String cleaned = addition.trim();
        cleaned = cleaned.replaceFirst("(?i)^(ti[ếe]p\\s*t[ụu]c|continue|continued|hoan\\s*tat)\\s*[:\\-]\\s*", "");
        return cleaned.trim();
    }

    private String mergeWithOverlap(String original, String addition) {
        if (!StringUtils.hasText(original)) {
            return addition == null ? "" : addition.trim();
        }
        if (!StringUtils.hasText(addition)) {
            return original.trim();
        }

        String base = original.trim();
        String extra = addition.trim();
        if (base.endsWith(extra)) {
            return base;
        }

        int maxOverlap = Math.min(Math.min(base.length(), extra.length()), 120);
        for (int overlap = maxOverlap; overlap >= 20; overlap--) {
            String baseSuffix = base.substring(base.length() - overlap);
            String extraPrefix = extra.substring(0, overlap);
            if (baseSuffix.equalsIgnoreCase(extraPrefix)) {
                extra = extra.substring(overlap).trim();
                break;
            }
        }

        if (!StringUtils.hasText(extra)) {
            return base;
        }

        char first = extra.charAt(0);
        if (Character.isWhitespace(first) || ".,!?;:)]}".indexOf(first) >= 0 || base.endsWith("\n")) {
            return base + extra;
        }
        return base + " " + extra;
    }

    private void broadcastAiTyping(String conversationId, boolean typing) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", AI_SENDER_ID);
        payload.put("displayName", AI_CONVERSATION_NAME);
        payload.put("typing", typing);
        payload.put("isAi", true);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + conversationId + "/typing",
                payload);
    }

    private AiChatResponse handleImageGenerationRequest(
            String userId,
            ChatWithAiRequest request,
            Conversations conversation,
            Message userMessage,
            QuickCommand imageCommand) {
        MessageResponse userMessageResponse = messageMapper.toResponse(userMessage);
        String language = resolveResponseLanguage(request.getLanguage());

        String providerStatus = "COMPLETED";
        boolean fallbackUsed = false;
        Message assistantMessage;

        try {
            AiImageWorkflowService.GeneratedImageResult result = aiImageWorkflowService
                    .generateAndStore(userId, imageCommand.argument(), imageCommand.key());

            Message imageMessage = Message.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(conversation.getConversationId())
                    .senderId(AI_SENDER_ID)
                    .role(AiRole.ASSISTANT)
                    .messageType(MessageType.IMAGE)
                    .content(result.imageUrl())
                    .fileName(result.fileName())
                    .fileSize(result.fileSize())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .isDeleted(false)
                    .isRecalled(false)
                    .isEdited(false)
                    .aiGenerated(true)
                    .aiModel("image:" + result.style())
                    .aiStatus(AiMessageStatus.COMPLETED)
                    .promptTokens(estimateTokenCount(userMessage.getContent()))
                    .completionTokens(estimateTokenCount(result.imageUrl()))
                    .totalTokens(estimateTokenCount(userMessage.getContent()) + estimateTokenCount(result.imageUrl()))
                    .aiRequestId(UUID.randomUUID().toString())
                    .build();

            imageMessage = messageRepository.save(imageMessage);

            String confirmationText = "vi".equals(language)
                    ? "Mình đã tạo ảnh xong rồi, bạn xem ngay bên dưới nhé."
                    : "Your image is ready. You can view it right below.";

            assistantMessage = Message.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(conversation.getConversationId())
                    .senderId(AI_SENDER_ID)
                    .role(AiRole.ASSISTANT)
                    .messageType(MessageType.TEXT)
                    .content(confirmationText)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .isDeleted(false)
                    .isRecalled(false)
                    .isEdited(false)
                    .aiGenerated(true)
                    .aiModel("image:" + result.style())
                    .aiStatus(AiMessageStatus.COMPLETED)
                    .promptTokens(estimateTokenCount(userMessage.getContent()))
                    .completionTokens(estimateTokenCount(confirmationText))
                    .totalTokens(estimateTokenCount(userMessage.getContent()) + estimateTokenCount(confirmationText))
                    .aiRequestId(UUID.randomUUID().toString())
                    .build();
            assistantMessage = messageRepository.save(assistantMessage);

            searchService.indexMessage(assistantMessage, "Fruvia Chatbot");

            updateConversationLastMessage(conversation, assistantMessage);

            List<ConversationMember> members = conversationMemberRepository
                    .findByConversationId(conversation.getConversationId());
            ConversationResponse conversationResponse = conversationMapper.toResponse(conversation, members, userId);

            MessageResponse imageMessageResponse = messageMapper.toResponse(imageMessage);
            MessageResponse assistantMessageResponse = messageMapper.toResponse(assistantMessage);

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversation.getConversationId(),
                    MessageAndConversationResponse.builder()
                            .message(imageMessageResponse)
                            .conversation(conversationResponse)
                            .build());

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversation.getConversationId(),
                    MessageAndConversationResponse.builder()
                            .message(assistantMessageResponse)
                            .conversation(conversationResponse)
                            .build());

            return AiChatResponse.builder()
                    .userMessage(userMessageResponse)
                    .imageMessage(imageMessageResponse)
                    .assistantMessage(assistantMessageResponse)
                    .conversation(conversationResponse)
                    .fallbackUsed(false)
                    .providerStatus(providerStatus)
                    .build();
        } catch (Exception ex) {
            log.warn("Image workflow failed, falling back to text AI response: {}", ex.getMessage());

            // Fall back to text AI response so the user still gets something useful
            try {
                List<Message> recentMessages = messageRepository
                        .findByConversationIdOrderByCreatedAtDesc(conversation.getConversationId(),
                                PageRequest.of(0, 30))
                        .getContent();
                List<Message> slidingWindow = buildSlidingWindow(recentMessages);
                String summary = updateSummaryIfNeeded(conversation, recentMessages, slidingWindow);
                List<String> ragContexts = getRagContexts(conversation.getConversationId(), request);

                String fallbackInstruction = "vi".equals(language)
                        ? "Dịch vụ tạo ảnh hiện không khả dụng. Hãy thông báo người dùng rằng không thể tạo ảnh lúc này, đề xuất thử lại sau, và nếu có thể hãy mô tả bằng lời những gì họ yêu cầu."
                        : "Image generation service is currently unavailable. Inform the user that the image cannot be created right now, suggest retrying later, and if possible describe in words what they requested.";

                List<Map<String, String>> promptMessages = buildPrompt(summary, ragContexts, slidingWindow,
                        request.getLanguage(), request.getThemeType(), null, null, null);
                promptMessages.add(createPromptMessage("system", fallbackInstruction));

                AiCompletionResult fallbackCompletion = null;
                try {
                    fallbackCompletion = blackboxAiClient.complete(promptMessages, defaultModel);
                } catch (Exception aiEx) {
                    log.warn("Text AI fallback also failed: {}", aiEx.getMessage());
                }

                String assistantContent;
                AiMessageStatus aiStatus;
                if (fallbackCompletion != null && StringUtils.hasText(fallbackCompletion.getContent())) {
                    assistantContent = fallbackCompletion.getContent();
                    aiStatus = AiMessageStatus.COMPLETED;
                } else {
                    assistantContent = "vi".equals(language)
                            ? "Mình chưa tạo được ảnh lúc này (dịch vụ đang tạm lỗi hoặc không truy cập được). Bạn thử lại sau ít phút nhé."
                            : "I couldn't generate the image right now (service unavailable). Please try again in a moment.";
                    aiStatus = AiMessageStatus.FAILED;
                }

                assistantMessage = Message.builder()
                        .messageId(UUID.randomUUID().toString())
                        .conversationId(conversation.getConversationId())
                        .senderId(AI_SENDER_ID)
                        .role(AiRole.ASSISTANT)
                        .messageType(MessageType.TEXT)
                        .content(assistantContent)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .isDeleted(false)
                        .isRecalled(false)
                        .isEdited(false)
                        .aiGenerated(true)
                        .aiModel(fallbackCompletion != null ? defaultModel : "image")
                        .aiStatus(aiStatus)
                        .promptTokens(estimateTokenCount(userMessage.getContent()))
                        .completionTokens(estimateTokenCount(assistantContent))
                        .totalTokens(
                                estimateTokenCount(userMessage.getContent())
                                        + estimateTokenCount(assistantContent))
                        .aiRequestId(UUID.randomUUID().toString())
                        .aiErrorCode("IMAGE_WORKFLOW_FAILED")
                        .aiErrorMessage(ex.getMessage())
                        .build();
            } catch (Exception fallbackEx) {
                log.error("Image fallback to text AI also failed: {}", fallbackEx.getMessage());
                String failedText = "vi".equals(language)
                        ? "Mình chưa tạo được ảnh lúc này (dịch vụ đang tạm lỗi hoặc không truy cập được). Bạn thử lại sau ít phút nhé."
                        : "I couldn't generate the image right now (service unavailable). Please try again in a moment.";
                assistantMessage = Message.builder()
                        .messageId(UUID.randomUUID().toString())
                        .conversationId(conversation.getConversationId())
                        .senderId(AI_SENDER_ID)
                        .role(AiRole.ASSISTANT)
                        .messageType(MessageType.TEXT)
                        .content(failedText)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .isDeleted(false)
                        .isRecalled(false)
                        .isEdited(false)
                        .aiGenerated(true)
                        .aiModel("image")
                        .aiStatus(AiMessageStatus.FAILED)
                        .promptTokens(estimateTokenCount(userMessage.getContent()))
                        .completionTokens(estimateTokenCount(failedText))
                        .totalTokens(
                                estimateTokenCount(userMessage.getContent()) + estimateTokenCount(failedText))
                        .aiRequestId(UUID.randomUUID().toString())
                        .aiErrorCode("IMAGE_WORKFLOW_FAILED")
                        .aiErrorMessage(ex.getMessage())
                        .build();
            }

            assistantMessage = messageRepository.save(assistantMessage);
            updateConversationLastMessage(conversation, assistantMessage);

            List<ConversationMember> members = conversationMemberRepository
                    .findByConversationId(conversation.getConversationId());
            ConversationResponse conversationResponse = conversationMapper.toResponse(conversation, members, userId);
            MessageResponse assistantMessageResponse = messageMapper.toResponse(assistantMessage);

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversation.getConversationId(),
                    MessageAndConversationResponse.builder()
                            .message(assistantMessageResponse)
                            .conversation(conversationResponse)
                            .build());

            return AiChatResponse.builder()
                    .userMessage(userMessageResponse)
                    .imageMessage(null)
                    .assistantMessage(assistantMessageResponse)
                    .conversation(conversationResponse)
                    .fallbackUsed(true)
                    .providerStatus("FAILED")
                    .build();
        }
    }

    private QuickCommand parseImageGenerationCommand(String content) {
        QuickCommand command = parseQuickCommand(content);
        if (command == null && StringUtils.hasText(content)) {
            command = parseDirectImageCommand(content);
        }
        if (command == null) {
            return null;
        }

        boolean isImageCommand = command.key().startsWith("image.");
        if (!isImageCommand || !StringUtils.hasText(command.argument())) {
            return null;
        }

        return command;
    }

    public SseEmitter chatWithAiStream(String userId, ChatWithAiRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("start").data(Map.of("status", "STREAMING")));

                AiChatResponse response = chatWithAi(userId, request);
                String content = response.getAssistantMessage() != null
                        ? response.getAssistantMessage().getContent()
                        : "";

                for (String chunk : splitByChunk(content, 40)) {
                    emitter.send(SseEmitter.event().name("delta").data(Map.of("chunk", chunk)));
                }

                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("code", "AI_STREAM_FAILED", "message", ex.getMessage())));
                } catch (Exception ignored) {
                    log.warn("Unable to push SSE error payload: {}", ignored.getMessage());
                }
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    private Message createUserMessage(String conversationId, String userId, String content, LocalDateTime now) {
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(userId)
                .role(AiRole.USER)
                .messageType(MessageType.TEXT)
                .content(content)
                .isDeleted(false)
                .isEdited(false)
                .isRecalled(false)
                .aiGenerated(false)
                .promptTokens(estimateTokenCount(content))
                .completionTokens(0)
                .totalTokens(estimateTokenCount(content))
                .createdAt(now)
                .updatedAt(now)
                .build();

        return messageRepository.save(message);
    }

    private Conversations resolveConversation(String userId, String conversationId, LocalDateTime now) {
        if (StringUtils.hasText(conversationId)) {
            Conversations existing = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));

            conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của cuộc hội thoại này"));
            return existing;
        }

        return conversationRepository
                .findFirstByCreatorIdAndConversationTypeAndConversationNameAndIsDeletedFalse(
                        userId,
                        ConversationType.SELF,
                        AI_CONVERSATION_NAME)
                .orElseGet(() -> {
                    Conversations created = Conversations.builder()
                            .conversationId(UUID.randomUUID().toString())
                            .conversationType(ConversationType.SELF)
                            .conversationStatus(ConversationStatus.NORMAL)
                            .conversationName(AI_CONVERSATION_NAME)
                            .participants(List.of(userId))
                            .creatorId(userId)
                            .isDeleted(false)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    created = conversationRepository.save(created);

                    conversationMemberRepository.save(ConversationMember.builder()
                            .conversationId(created.getConversationId())
                            .userId(userId)
                            .role(MemberRole.MEMBER)
                            .joinedAt(now)
                            .build());

                    // Gửi tin nhắn chào mừng từ AI khi tạo conversation mới
                    String userName = userDetailRepository.findByUserId(userId)
                            .map(UserDetail::getDisplayName)
                            .orElse(null);
                    String welcomeContent = (userName != null && !userName.isBlank())
                            ? "Chào mừng bạn trở lại **" + userName
                                    + "**! 👋\n\nTôi là **Fruvia Chatbot** — trợ lý thông minh của bạn. Hãy hỏi tôi bất cứ điều gì nhé! 😊"
                            : "Xin chào! 👋\n\nTôi là **Fruvia Chatbot** — trợ lý thông minh của bạn. Hãy hỏi tôi bất cứ điều gì nhé! 😊";
                    Message welcomeMessage = Message.builder()
                            .messageId(UUID.randomUUID().toString())
                            .conversationId(created.getConversationId())
                            .senderId(AI_SENDER_ID)
                            .role(AiRole.ASSISTANT)
                            .messageType(MessageType.TEXT)
                            .content(welcomeContent)
                            .isDeleted(false)
                            .isEdited(false)
                            .isRecalled(false)
                            .aiGenerated(true)
                            .aiStatus(AiMessageStatus.COMPLETED)
                            .promptTokens(0)
                            .completionTokens(estimateTokenCount(welcomeContent))
                            .totalTokens(estimateTokenCount(welcomeContent))
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    welcomeMessage = messageRepository.save(welcomeMessage);
                    created.setLastMessageId(welcomeMessage.getMessageId());
                    created.setLastMessageContent(welcomeContent);
                    created.setLastMessageTime(now);
                    created.setLastMessageSenderId(AI_SENDER_ID);
                    created.setLastMessageSenderName(AI_CONVERSATION_NAME);
                    created = conversationRepository.save(created);

                    return created;
                });
    }

    private void updateConversationLastMessage(Conversations conversation, Message message) {
        conversation.setLastMessageId(message.getMessageId());
        conversation.setLastMessageContent(message.getContent());
        conversation.setLastMessageTime(message.getCreatedAt());
        conversation.setLastMessageSenderId(message.getSenderId());

        if (AI_SENDER_ID.equals(message.getSenderId())) {
            conversation.setLastMessageSenderName(AI_CONVERSATION_NAME);
        } else {
            userDetailRepository.findByUserId(message.getSenderId())
                    .map(UserDetail::getDisplayName)
                    .ifPresent(conversation::setLastMessageSenderName);
        }

        conversation.setUpdatedAt(message.getCreatedAt());
        conversationRepository.save(conversation);
    }

    private List<Message> buildSlidingWindow(List<Message> recentMessagesDesc) {
        List<Message> selectedDesc = new ArrayList<>();
        int usedTokens = 0;

        for (Message message : recentMessagesDesc) {
            if (!isUsableForPrompt(message)) {
                continue;
            }

            int messageTokens = estimateTokenCount(message.getContent());
            if (selectedDesc.size() >= slidingWindowSize) {
                break;
            }
            if (usedTokens + messageTokens > windowTokenBudget && !selectedDesc.isEmpty()) {
                break;
            }

            selectedDesc.add(message);
            usedTokens += messageTokens;
        }

        Collections.reverse(selectedDesc);
        return selectedDesc;
    }

    private String updateSummaryIfNeeded(Conversations conversation, List<Message> recentDesc,
            List<Message> slidingWindow) {
        if (recentDesc.size() <= slidingWindow.size()) {
            return conversation.getAiSummary();
        }

        List<String> summaryItems = new ArrayList<>();
        for (int i = slidingWindow.size(); i < Math.min(recentDesc.size(), slidingWindow.size() + 5); i++) {
            Message msg = recentDesc.get(i);
            if (!isUsableForPrompt(msg)) {
                continue;
            }
            summaryItems.add(resolveRole(msg) + ": " + trimContent(msg.getContent(), 120));
        }

        if (summaryItems.isEmpty()) {
            return conversation.getAiSummary();
        }

        String summary = "Older context summary: " + String.join(" | ", summaryItems);
        conversation.setAiSummary(summary);
        conversation.setAiSummaryUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        return summary;
    }

    private List<String> getRagContexts(String conversationId, ChatWithAiRequest request) {
        boolean useRag = request.getUseRag() == null || request.getUseRag();
        if (!useRag) {
            return List.of();
        }

        int topK = request.getRagTopK() == null ? 3 : Math.max(1, Math.min(8, request.getRagTopK()));
        try {
            Page<SearchResult<MessageDocument>> page = searchService.searchMessages(
                    request.getContent(),
                    conversationId,
                    0,
                    topK);

            return page.getContent().stream()
                    .map(SearchResult::getDocument)
                    .map(MessageDocument::getContent)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            log.warn("RAG context retrieval failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<Map<String, String>> buildPrompt(String summary, List<String> ragContexts,
            List<Message> windowMessages,
            String requestedLanguage,
            String themeType,
            String userProfileContext,
            String storageContext,
            String fullAccessContext) {
        List<Map<String, String>> prompt = new ArrayList<>();
        String language = resolveResponseLanguage(requestedLanguage);
        String themedPrompt = systemPromptLibrary.resolveSystemPrompt(themeType, language);

        prompt.add(createPromptMessage("system", themedPrompt));

        if ("vi".equals(language)) {
            prompt.add(createPromptMessage(
                    "system",
                    "Always respond in Vietnamese with full diacritical marks (dấu tiếng Việt). "
                            + "For example: 'xin chào' NOT 'xin chao', 'bạn khỏe không' NOT 'ban khoe khong'. "
                            + "Keep responses natural for Vietnamese users."));
        } else {
            prompt.add(createPromptMessage(
                    "system",
                    "Always respond in English."));
        }

        prompt.add(createPromptMessage("system", buildQuickCommandInstruction(language)));

        if (StringUtils.hasText(fullAccessContext)) {
            prompt.add(createPromptMessage("system", fullAccessContext));
        }

        if (StringUtils.hasText(userProfileContext)) {
            prompt.add(createPromptMessage("system", userProfileContext));
        }

        if (StringUtils.hasText(storageContext)) {
            prompt.add(createPromptMessage("system", storageContext));
        }

        if (StringUtils.hasText(summary)) {
            prompt.add(createPromptMessage("system", summary));
        }

        if (!ragContexts.isEmpty()) {
            String ragText = "Relevant long-term memory:\n- " + String.join("\n- ", ragContexts);
            prompt.add(createPromptMessage("system", ragText));
        }

        for (Message message : windowMessages) {
            String role = resolveRole(message);
            String content = sanitizeContentForPrompt(message.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if ("user".equals(role)) {
                content = normalizeAiShortcutCommand(content, language);
            }
            prompt.add(createPromptMessage(role, content));
        }

        return prompt;
    }

    private Map<String, String> createPromptMessage(String role, String content) {
        Map<String, String> payload = new HashMap<>();
        payload.put("role", role);
        payload.put("content", content);
        return payload;
    }

    private String resolveRole(Message message) {
        if (message.getRole() != null) {
            return message.getRole().name().toLowerCase();
        }
        return AI_SENDER_ID.equals(message.getSenderId()) ? "assistant" : "user";
    }

    private boolean isUsableForPrompt(Message message) {
        return !Boolean.TRUE.equals(message.getIsDeleted())
                && !Boolean.TRUE.equals(message.getIsRecalled())
                && StringUtils.hasText(message.getContent())
                && (message.getMessageType() == null || MessageType.TEXT.equals(message.getMessageType()));
    }

    private int estimatePromptTokenCount(List<Map<String, String>> promptMessages) {
        int total = 0;
        for (Map<String, String> msg : promptMessages) {
            total += estimateTokenCount(msg.get("content"));
        }
        return total;
    }

    private int estimateTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String trimContent(String content, int maxLength) {
        if (!StringUtils.hasText(content) || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private List<String> splitByChunk(String text, int chunkSize) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(text.length(), i + chunkSize)));
        }
        return chunks;
    }

    private String resolveResponseLanguage(String requestedLanguage) {
        if (!StringUtils.hasText(requestedLanguage)) {
            return "vi";
        }
        String normalized = requestedLanguage.trim().toLowerCase();
        return normalized.startsWith("en") ? "en" : "vi";
    }

    private String buildQuickCommandInstruction(String language) {
        if ("vi".equals(language)) {
            return "Lệnh nhanh thân thiện: 'tên tôi', 'ngày sinh tôi', 'hồ sơ tôi', 'liệt kê file', 'xóa file <tên_file>', 'xóa nhánh <tên_nhánh>', '/image <mô_tả>', '/image_pro <mô_tả>', '/sketch <mô_tả>', '/wallpaper <mô_tả>'. "
                    + "Vẫn hỗ trợ lệnh cũ dạng /me.name, /files.list... Nếu người dùng gửi các cụm này, hãy xử lý đúng ý nghĩa và trả lời ngắn gọn. "
                    + "QUAN TRỌNG: Luôn trả lời bằng tiếng Việt có dấu đầy đủ (ví dụ: 'xin chào' chứ không phải 'xin chao').";
        }
        return "Friendly quick commands: 'my name', 'my birthday', 'my profile', 'list files', 'delete file <file_name>', 'delete branch <branch_name>', '/image <description>', '/image_pro <description>', '/sketch <description>', '/wallpaper <description>'. "
                + "Legacy slash commands are still supported. If user uses these phrases, interpret by intent and respond clearly.";
    }

    private String normalizeAiShortcutCommand(String content, String language) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        QuickCommand quickCommand = parseQuickCommand(content);
        if (quickCommand == null) {
            return content;
        }

        String argument = quickCommand.argument();
        boolean isVi = "vi".equals(language);

        return switch (quickCommand.key()) {
            case "me.name" -> isVi
                    ? "Cho toi biet ten hien thi hien tai cua toi la gi."
                    : "Tell me my current display name.";
            case "me.dob" -> isVi
                    ? "Cho toi biet ngay thang nam sinh hien co trong ho so cua toi."
                    : "Tell me the date of birth currently stored in my profile.";
            case "me.profile" -> isVi
                    ? "Hay tom tat toan bo thong tin ho so hien co cua toi that gon gang."
                    : "Summarize all currently available profile information about me concisely.";
            case "files.list" -> isVi
                    ? "Hay liet ke cac file cua toi va de xuat cach loc/tim nhanh theo ten, dinh dang hoac thoi gian."
                    : "List my files and suggest quick ways to filter/search by name, type, or time.";
            case "files.delete" -> isVi
                    ? "Hay huong dan toi xoa file"
                            + (StringUtils.hasText(argument) ? " co ten '" + argument + "'" : "")
                            + " mot cach an toan theo tung buoc."
                    : "Guide me to safely delete file"
                            + (StringUtils.hasText(argument) ? " named '" + argument + "'" : "")
                            + " step by step.";
            case "branch.delete" -> isVi
                    ? "Hay huong dan toi xoa nhanh"
                            + (StringUtils.hasText(argument) ? " '" + argument + "'" : "")
                            + " an toan va nhac toi cac rui ro can kiem tra truoc."
                    : "Guide me to safely delete branch"
                            + (StringUtils.hasText(argument) ? " '" + argument + "'" : "")
                            + " and mention key safety checks first.";
            case "image.generate" -> isVi
                    ? "Hay tao anh theo mo ta: " + argument
                    : "Generate an image from this description: " + argument;
            case "image.pro" -> isVi
                    ? "Hay tao anh ky thuat so 4K theo mo ta: " + argument
                    : "Generate a high-quality 4K digital image with this description: " + argument;
            case "image.sketch" -> isVi
                    ? "Hay tao anh phac thao den trang theo mo ta: " + argument
                    : "Generate a black-and-white sketch from this description: " + argument;
            case "image.wallpaper" -> isVi
                    ? "Hay tao anh nen do phan giai cao theo mo ta: " + argument
                    : "Generate a high-resolution wallpaper from this description: " + argument;
            default -> content;
        };
    }

    private QuickCommand parseQuickCommand(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return null;
        }

        String content = normalizeWhitespace(normalizeLeadingSlash(stripZeroWidthChars(rawContent))).trim();
        if (!StringUtils.hasText(content)) {
            return null;
        }

        if (content.startsWith("/")) {
            String[] parts = content.split("\\s+", 2);
            String command = normalizeSlashCommandToken(parts[0]);
            String argument = parts.length > 1 ? parts[1].trim() : "";
            return switch (command) {
                case "/me.name" -> new QuickCommand("me.name", argument);
                case "/me.dob" -> new QuickCommand("me.dob", argument);
                case "/me.profile" -> new QuickCommand("me.profile", argument);
                case "/files.list" -> new QuickCommand("files.list", argument);
                case "/files.delete" -> new QuickCommand("files.delete", argument);
                case "/branch.delete" -> new QuickCommand("branch.delete", argument);
                case "/image" -> new QuickCommand("image.generate", argument);
                case "/image_pro" -> new QuickCommand("image.pro", argument);
                case "/sketch" -> new QuickCommand("image.sketch", argument);
                case "/wallpaper" -> new QuickCommand("image.wallpaper", argument);
                default -> null;
            };
        }

        String normalized = normalizeForMatch(content);

        if (matchesAny(normalized, "ten toi", "toi ten gi", "ten cua toi", "my name", "what is my name")) {
            return new QuickCommand("me.name", "");
        }
        if (matchesAny(normalized, "ngay sinh toi", "sinh nhat toi", "ngay thang nam sinh toi", "my birthday",
                "my dob")) {
            return new QuickCommand("me.dob", "");
        }
        if (matchesAny(normalized, "ho so toi", "thong tin cua toi", "profile toi", "my profile", "profile summary")) {
            return new QuickCommand("me.profile", "");
        }
        if (matchesAny(normalized, "liet ke file", "danh sach file", "file cua toi", "list files", "my files")) {
            return new QuickCommand("files.list", "");
        }

        if (normalized.startsWith("xoa file ") || normalized.startsWith("delete file ")) {
            return new QuickCommand("files.delete", extractArgument(content, 2));
        }

        if (normalized.startsWith("xoa nhanh ") || normalized.startsWith("xoa branch ")
                || normalized.startsWith("delete branch ")) {
            return new QuickCommand("branch.delete", extractArgument(content, 2));
        }

        if (normalized.startsWith("tao anh:") || normalized.startsWith("tao hinh anh:")
                || normalized.startsWith("generate image:")
                || normalized.startsWith("create image:")) {
            return new QuickCommand("image.generate", extractAfterColon(content));
        }

        if (normalized.startsWith("tao anh 4k:") || normalized.startsWith("tao hinh anh 4k:")
                || normalized.startsWith("image pro:")) {
            return new QuickCommand("image.pro", extractAfterColon(content));
        }

        if (normalized.startsWith("ve phac thao:") || normalized.startsWith("sketch:")) {
            return new QuickCommand("image.sketch", extractAfterColon(content));
        }

        if (normalized.startsWith("tao wallpaper:") || normalized.startsWith("wallpaper:")) {
            return new QuickCommand("image.wallpaper", extractAfterColon(content));
        }

        if (normalized.startsWith("tao anh ") || normalized.startsWith("tao hinh anh ")
                || normalized.startsWith("generate image ")
                || normalized.startsWith("create image ")) {
            return new QuickCommand("image.generate",
                    normalized.startsWith("tao hinh anh ") ? extractArgument(content, 3) : extractArgument(content, 2));
        }

        if (normalized.startsWith("tao anh 4k ") || normalized.startsWith("tao hinh anh 4k ")
                || normalized.startsWith("image pro ")) {
            return new QuickCommand("image.pro",
                    (normalized.startsWith("tao anh 4k ") || normalized.startsWith("tao hinh anh 4k "))
                            ? extractArgument(content, 4)
                            : extractArgument(content, 2));
        }

        if (normalized.startsWith("ve phac thao ") || normalized.startsWith("sketch ")) {
            return new QuickCommand("image.sketch",
                    normalized.startsWith("ve phac thao ") ? extractArgument(content, 3) : extractArgument(content, 1));
        }

        if (normalized.startsWith("tao wallpaper ") || normalized.startsWith("wallpaper ")) {
            return new QuickCommand("image.wallpaper",
                    normalized.startsWith("tao wallpaper ") ? extractArgument(content, 2)
                            : extractArgument(content, 1));
        }

        return null;
    }

    private QuickCommand parseDirectImageCommand(String content) {
        Matcher matcher = DIRECT_IMAGE_COMMAND_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return null;
        }

        String rawKey = matcher.group(1) == null ? "" : matcher.group(1).toLowerCase(Locale.ROOT);
        String argument = matcher.group(2) == null ? "" : matcher.group(2).trim();

        String mappedKey = switch (rawKey) {
            case "image_pro" -> "image.pro";
            case "sketch" -> "image.sketch";
            case "wallpaper" -> "image.wallpaper";
            default -> "image.generate";
        };

        return new QuickCommand(mappedKey, argument);
    }

    private String buildFullAccessContext(String userId, String conversationId, String requestedLanguage) {
        String language = resolveResponseLanguage(requestedLanguage);

        try {
            UserDetail detail = userDetailRepository.findByUserId(userId).orElse(null);
            UserAuth auth = userAuthRepository.findById(userId).orElse(null);
            UserSetting setting = userSettingRepository.findByUserId(userId).orElse(null);

            List<Message> recentUserMessages = messageRepository
                    .findBySenderIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 60))
                    .getContent();
            List<Message> recentConversationMessages = messageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 40))
                    .getContent();
            List<FileUpload> files = fileUploadRepository.findByUploadedBy(userId);
            List<Conversations> privateConversations = conversationRepository
                    .findPrivateConversationsByParticipant(userId);
            List<Friendship> acceptedFriends = friendshipRepository.findAllAcceptedFriends(userId);

            Map<String, Object> profile = buildProfileSnapshot(detail, auth, setting, recentUserMessages);
            Map<String, Object> history = buildHistorySnapshot(recentConversationMessages, recentUserMessages);
            Map<String, Object> cloudMetadata = buildCloudMetadataSnapshot(files);
            Map<String, Object> behavior = buildBehaviorSnapshot(recentUserMessages, requestedLanguage);
            Map<String, Object> relationships = buildRelationshipSnapshot(userId, privateConversations,
                    acceptedFriends);

            String profileJson = toJson(profile);
            String historyJson = toJson(history);
            String cloudJson = toJson(cloudMetadata);
            String behaviorJson = toJson(behavior);
            String relationshipsJson = toJson(relationships);

            if ("vi".equals(language)) {
                return """
                        FULL DATA ACCESS GRANTED cho nguoi dung hien tai.
                        Su dung du lieu ben duoi de ca nhan hoa cau tra loi, uu tien thong tin lien quan truc tiep den cau hoi.

                        [USER PROFILE]
                        %s

                        [RECENT CONVERSATIONS & HISTORY]
                        %s

                        [PERSONAL CLOUD & FILE METADATA]
                        %s

                        [USER BEHAVIOR PATTERNS]
                        %s

                        [RELATIONSHIP CONTEXT]
                        %s

                        Quy tac bat buoc:
                        - Khong tu tiet lo du lieu nhay cam neu nguoi dung khong hoi truc tiep.
                        - Khi thong tin khong chac chan, phai noi ro muc do chac chan.
                        - Khong tu dong thuc hien hanh dong xoa/chinh sua du lieu; chi huong dan va xac nhan.
                        - Tra loi ngan gon, dung trong tam cau hoi hien tai.
                        """
                        .formatted(profileJson, historyJson, cloudJson, behaviorJson, relationshipsJson);
            }

            return """
                    FULL DATA ACCESS GRANTED for the current authenticated user.
                    Use the data below to personalize answers and prioritize information relevant to the current request.

                    [USER PROFILE]
                    %s

                    [RECENT CONVERSATIONS & HISTORY]
                    %s

                    [PERSONAL CLOUD & FILE METADATA]
                    %s

                    [USER BEHAVIOR PATTERNS]
                    %s

                    [RELATIONSHIP CONTEXT]
                    %s

                    Mandatory rules:
                    - Do not expose sensitive data unless the user asks for it explicitly.
                    - When confidence is low, state uncertainty clearly.
                    - Never perform destructive actions automatically; provide guidance and ask for confirmation.
                    - Keep responses concise and focused on current intent.
                    """
                    .formatted(profileJson, historyJson, cloudJson, behaviorJson, relationshipsJson);
        } catch (Exception ex) {
            log.warn("Failed to build full access context: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildProfileSnapshot(
            UserDetail detail,
            UserAuth auth,
            UserSetting setting,
            List<Message> recentUserMessages) {
        Map<String, Object> profile = new LinkedHashMap<>();

        String displayName = detail != null ? extractDisplayName(detail) : null;
        profile.put("display_name", displayName);
        profile.put("first_name", detail != null ? detail.getFirstName() : null);
        profile.put("last_name", detail != null ? detail.getLastName() : null);
        profile.put("dob", detail != null ? formatDate(detail.getDob()) : null);
        profile.put("gender", detail != null ? detail.getGender() : null);
        profile.put("city", detail != null ? detail.getCity() : null);
        profile.put("address", detail != null ? detail.getAddress() : null);
        profile.put("education", detail != null ? detail.getEducation() : null);
        profile.put("workplace", detail != null ? detail.getWorkplace() : null);
        profile.put("bio", detail != null ? trimContent(redactSensitiveInfo(detail.getBio()), 300) : null);

        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("gmail", detail != null ? redactSensitiveInfo(detail.getGmail()) : null);
        contact.put("last_login_at",
                auth != null && auth.getLastLoginAt() != null ? auth.getLastLoginAt().toString() : null);
        profile.put("contact", contact);

        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("allow_friend_requests", setting != null ? setting.getAllowFriendRequests() : null);
        privacy.put("who_can_see_profile", setting != null && setting.getWhoCanSeeProfile() != null
                ? setting.getWhoCanSeeProfile().name()
                : null);
        privacy.put("who_can_send_messages", setting != null && setting.getWhoCanSendMessages() != null
                ? setting.getWhoCanSendMessages().name()
                : null);
        privacy.put("show_online_status", setting != null ? setting.getShowOnlineStatus() : null);
        profile.put("privacy_preferences", privacy);

        profile.put("inferred_interests", inferInterests(detail != null ? detail.getBio() : null, recentUserMessages));

        return profile;
    }

    private Map<String, Object> buildHistorySnapshot(List<Message> recentConversationMessages,
            List<Message> recentUserMessages) {
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("recent_current_conversation", mapMessagesForContext(recentConversationMessages, 20));
        history.put("recent_user_messages_global", mapMessagesForContext(recentUserMessages, 20));

        if (recentUserMessages == null || recentUserMessages.isEmpty()) {
            history.put("message_count_window", 0);
            history.put("avg_user_message_length", 0);
            return history;
        }

        int messageCount = recentUserMessages.size();
        double avgLen = recentUserMessages.stream()
                .filter(this::isUsableForPrompt)
                .map(Message::getContent)
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .average()
                .orElse(0);

        history.put("message_count_window", messageCount);
        history.put("avg_user_message_length", Math.round(avgLen));
        return history;
    }

    private Map<String, Object> buildCloudMetadataSnapshot(List<FileUpload> files) {
        Map<String, Object> cloud = new LinkedHashMap<>();

        List<FileUpload> activeFiles = files == null
                ? List.of()
                : files.stream()
                        .filter(file -> !"DELETED".equalsIgnoreCase(file.getStatus()))
                        .sorted(Comparator.comparing(FileUpload::getUploadedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();

        cloud.put("total_files", activeFiles.size());
        cloud.put("total_size_bytes", activeFiles.stream()
                .map(FileUpload::getFileSize)
                .filter(size -> size != null && size > 0)
                .reduce(0L, Long::sum));

        Map<String, Long> fileTypeCount = activeFiles.stream()
                .map(FileUpload::getFileType)
                .filter(type -> type != null)
                .collect(Collectors.groupingBy(Enum::name, Collectors.counting()));
        cloud.put("file_type_distribution", fileTypeCount);

        List<Map<String, Object>> recentFiles = activeFiles.stream()
                .limit(20)
                .map(file -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("name", trimContent(redactSensitiveInfo(file.getOriginalFileName()), 120));
                    payload.put("stored_name", trimContent(redactSensitiveInfo(file.getStoredFileName()), 120));
                    payload.put("mime_type", file.getMimeType());
                    payload.put("file_type", file.getFileType() != null ? file.getFileType().name() : null);
                    payload.put("size_bytes", file.getFileSize());
                    payload.put("uploaded_at", file.getUploadedAt() != null ? file.getUploadedAt().toString() : null);
                    payload.put("provider",
                            file.getStorageProvider() != null ? file.getStorageProvider().name() : null);
                    payload.put("folder", trimContent(file.getFolderPath(), 120));
                    return payload;
                })
                .toList();
        cloud.put("recent_files", recentFiles);

        return cloud;
    }

    private Map<String, Object> buildBehaviorSnapshot(List<Message> recentUserMessages, String requestedLanguage) {
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("common_commands", extractCommonCommands(recentUserMessages));
        behavior.put("active_hours", extractTopActiveHours(recentUserMessages));

        String language = resolveResponseLanguage(requestedLanguage);
        behavior.put("preferred_response_language", "vi".equals(language) ? "Vietnamese" : "English");

        long conciseMessages = recentUserMessages == null ? 0
                : recentUserMessages.stream()
                        .filter(this::isUsableForPrompt)
                        .map(Message::getContent)
                        .filter(StringUtils::hasText)
                        .filter(content -> content.length() <= 80)
                        .count();
        long longMessages = recentUserMessages == null ? 0
                : recentUserMessages.stream()
                        .filter(this::isUsableForPrompt)
                        .map(Message::getContent)
                        .filter(StringUtils::hasText)
                        .filter(content -> content.length() >= 240)
                        .count();

        if (conciseMessages > longMessages) {
            behavior.put("response_style_hint", "prefer concise");
        } else if (longMessages > conciseMessages) {
            behavior.put("response_style_hint", "allow detailed");
        } else {
            behavior.put("response_style_hint", "balanced");
        }

        return behavior;
    }

    private Map<String, Object> buildRelationshipSnapshot(
            String userId,
            List<Conversations> privateConversations,
            List<Friendship> acceptedFriends) {
        Map<String, Object> relationships = new LinkedHashMap<>();

        Set<String> acceptedFriendIds = acceptedFriends == null
                ? Set.of()
                : acceptedFriends.stream()
                        .map(friendship -> userId.equals(friendship.getRequesterId())
                                ? friendship.getReceiverId()
                                : friendship.getRequesterId())
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());

        List<Map<String, Object>> topRelationships = (privateConversations == null ? List.<Conversations>of()
                : privateConversations)
                .stream()
                .map(conversation -> {
                    String peerId = conversation.getParticipants() == null
                            ? null
                            : conversation.getParticipants().stream()
                                    .filter(participant -> !userId.equals(participant))
                                    .findFirst()
                                    .orElse(null);

                    long interactionCount = messageRepository
                            .countByConversationIdAndIsDeletedFalse(conversation.getConversationId());

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("peer_user_id", peerId);
                    payload.put("conversation_id", conversation.getConversationId());
                    payload.put("interaction_count", interactionCount);
                    payload.put("friendship_status", acceptedFriendIds.contains(peerId) ? "ACCEPTED" : "UNKNOWN");
                    payload.put("last_message_at",
                            conversation.getLastMessageTime() != null ? conversation.getLastMessageTime().toString()
                                    : null);
                    return payload;
                })
                .sorted((a, b) -> Long.compare(
                        ((Number) b.getOrDefault("interaction_count", 0L)).longValue(),
                        ((Number) a.getOrDefault("interaction_count", 0L)).longValue()))
                .limit(12)
                .toList();

        relationships.put("accepted_friend_count", acceptedFriendIds.size());
        relationships.put("top_relationships", topRelationships);
        return relationships;
    }

    private List<Map<String, String>> mapMessagesForContext(List<Message> messages, int limit) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        return messages.stream()
                .filter(this::isUsableForPrompt)
                .limit(Math.max(1, limit))
                .map(message -> {
                    Map<String, String> payload = new LinkedHashMap<>();
                    payload.put("role", resolveRole(message));
                    payload.put("content",
                            trimContent(redactSensitiveInfo(sanitizeContentForPrompt(message.getContent())), 240));
                    payload.put("created_at",
                            message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
                    return payload;
                })
                .toList();
    }

    private List<String> extractCommonCommands(List<Message> recentUserMessages) {
        if (recentUserMessages == null || recentUserMessages.isEmpty()) {
            return List.of();
        }

        Map<String, Long> commandCount = recentUserMessages.stream()
                .filter(this::isUsableForPrompt)
                .map(Message::getContent)
                .map(this::parseQuickCommand)
                .filter(command -> command != null)
                .collect(Collectors.groupingBy(QuickCommand::key, Collectors.counting()));

        return commandCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> extractTopActiveHours(List<Message> recentUserMessages) {
        if (recentUserMessages == null || recentUserMessages.isEmpty()) {
            return List.of();
        }

        Map<Integer, Long> hourCount = recentUserMessages.stream()
                .filter(this::isUsableForPrompt)
                .map(Message::getCreatedAt)
                .filter(createdAt -> createdAt != null)
                .collect(Collectors.groupingBy(LocalDateTime::getHour, Collectors.counting()));

        return hourCount.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(4)
                .map(entry -> String.format("%02d:00-%02d:59", entry.getKey(), entry.getKey()))
                .toList();
    }

    private List<String> inferInterests(String bio, List<Message> recentUserMessages) {
        StringBuilder source = new StringBuilder();
        if (StringUtils.hasText(bio)) {
            source.append(' ').append(normalizeForMatch(bio));
        }

        if (recentUserMessages != null) {
            recentUserMessages.stream()
                    .filter(this::isUsableForPrompt)
                    .map(Message::getContent)
                    .filter(StringUtils::hasText)
                    .limit(30)
                    .forEach(content -> source.append(' ').append(normalizeForMatch(content)));
        }

        String normalized = source.toString();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        Map<String, String[]> interestPatterns = new LinkedHashMap<>();
        interestPatterns.put("minecraft", new String[] { "minecraft" });
        interestPatterns.put("japanese", new String[] { "japanese", "nhat ban", "tieng nhat" });
        interestPatterns.put("gaming", new String[] { "game", "gaming", "esport" });
        interestPatterns.put("music", new String[] { "music", "am nhac", "song", "bai hat" });
        interestPatterns.put("travel", new String[] { "travel", "du lich", "trip" });
        interestPatterns.put("technology", new String[] { "coding", "code", "lap trinh", "cong nghe", "tech" });

        List<String> interests = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : interestPatterns.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalized.contains(keyword)) {
                    interests.add(entry.getKey());
                    break;
                }
            }
        }

        return interests.stream().distinct().limit(8).toList();
    }

    private String redactSensitiveInfo(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        String redacted = text;
        redacted = redacted.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[redacted_email]");
        redacted = redacted.replaceAll("\\b(?:\\+?\\d[\\d\\s-]{7,}\\d)\\b", "[redacted_phone]");
        redacted = redacted.replaceAll(
                "(?i)(password|otp|token|secret|api[_-]?key)\\s*[:=]?\\s*[^\\s,;]+",
                "$1=[redacted]");
        return redacted;
    }

    private String sanitizeContentForPrompt(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        // Prevent the model from repeating giant presigned URLs in normal chat replies.
        String sanitized = text.replaceAll("https?://\\S*X-Amz-Signature=\\S*", "[generated-image-link]");
        return sanitized;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("JSON serialization failed for full access context: {}", ex.getMessage());
            return String.valueOf(payload);
        }
    }

    private String buildStorageContextIfRequested(String userId, String content, String requestedLanguage) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        QuickCommand quickCommand = parseQuickCommand(content);
        if (quickCommand == null
                || !("files.list".equals(quickCommand.key()) || "files.delete".equals(quickCommand.key()))) {
            return null;
        }

        String language = resolveResponseLanguage(requestedLanguage);
        try {
            Map<String, Object> stats = storageService.getUserStorageStats(userId);
            Object itemsObj = stats.get("items");
            if (!(itemsObj instanceof List<?> items)) {
                return null;
            }

            if (items.isEmpty()) {
                return "vi".equals(language)
                        ? "Thong tin cloud: Hien tai khong co file nao."
                        : "Cloud data: There are currently no files.";
            }

            List<String> lines = new ArrayList<>();
            int maxItems = Math.min(items.size(), 25);
            for (int i = 0; i < maxItems; i++) {
                Object raw = items.get(i);
                if (!(raw instanceof Map<?, ?> map)) {
                    continue;
                }
                Object name = map.get("name");
                Object type = map.get("type");
                Object size = map.get("size");
                Object date = map.get("date");

                lines.add("- name=" + String.valueOf(name)
                        + ", type=" + String.valueOf(type)
                        + ", size=" + String.valueOf(size)
                        + ", date=" + String.valueOf(date));
            }

            String header = "vi".equals(language)
                    ? "Du lieu file cloud cua nguoi dung (uu tien su dung khi xu ly lenh /files.list hoặc /files.delete):"
                    : "User cloud file data (prioritize for /files.list or /files.delete intents):";

            String tail = "vi".equals(language)
                    ? "Neu la yeu cau xoa file, khong xoa thay nguoi dung. Hay dua huong dan tung buoc va xac nhan file truoc khi thao tac."
                    : "For delete requests, do not perform deletion directly. Provide safe step-by-step guidance and ask for file confirmation first.";

            return header + "\n" + String.join("\n", lines) + "\n" + tail;
        } catch (Exception ex) {
            log.warn("Failed to enrich AI with storage context: {}", ex.getMessage());
            return null;
        }
    }

    private String buildUserProfileContext(String userId, String requestedLanguage) {
        String language = resolveResponseLanguage(requestedLanguage);
        UserDetail detail = userDetailRepository.findByUserId(userId).orElse(null);
        UserAuth auth = userAuthRepository.findById(userId).orElse(null);

        List<String> fields = new ArrayList<>();
        if (detail != null) {
            String fullName = extractDisplayName(detail);
            appendIfHasText(fields, "display_name", fullName);
            appendIfHasText(fields, "first_name", detail.getFirstName());
            appendIfHasText(fields, "last_name", detail.getLastName());
            appendIfHasText(fields, "dob", formatDate(detail.getDob()));
            appendIfHasText(fields, "gender", detail.getGender());
            appendIfHasText(fields, "city", detail.getCity());
            appendIfHasText(fields, "address", detail.getAddress());
            appendIfHasText(fields, "education", detail.getEducation());
            appendIfHasText(fields, "workplace", detail.getWorkplace());
            appendIfHasText(fields, "bio", detail.getBio());
        }
        if (auth != null) {
            appendIfHasText(fields, "phone", auth.getPhoneNumber());
        }
        if (detail != null) {
            appendIfHasText(fields, "gmail", detail.getGmail());
        }

        if (fields.isEmpty()) {
            return null;
        }

        if ("vi".equals(language)) {
            return "Thong tin ho so cua nguoi dung hien tai (chi su dung khi nguoi dung hoi ve chinh ho):\n"
                    + String.join("\n", fields)
                    + "\nNeu cau hoi khong lien quan thong tin ca nhan, khong can nhac lai cac truong nay.";
        }

        return "Profile data of the current authenticated user (use only when user asks about themselves):\n"
                + String.join("\n", fields)
                + "\nIf the question is unrelated to personal profile, do not mention these fields.";
    }

    private void appendIfHasText(List<String> lines, String key, String value) {
        if (StringUtils.hasText(value)) {
            lines.add("- " + key + ": " + value.trim());
        }
    }

    private String extractDisplayName(UserDetail detail) {
        if (StringUtils.hasText(detail.getDisplayName())) {
            return detail.getDisplayName();
        }
        String fullName = ((detail.getFirstName() == null ? "" : detail.getFirstName()) + " "
                + (detail.getLastName() == null ? "" : detail.getLastName())).trim();
        return StringUtils.hasText(fullName) ? fullName : null;
    }

    private boolean matchesAny(String normalized, String... candidates) {
        for (String candidate : candidates) {
            if (normalized.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String extractArgument(String content, int skipTokens) {
        String[] tokens = content.trim().split("\\s+");
        if (tokens.length <= skipTokens) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = skipTokens; i < tokens.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(tokens[i]);
        }
        return builder.toString().trim();
    }

    private String extractAfterColon(String content) {
        int index = content.indexOf(':');
        if (index < 0 || index + 1 >= content.length()) {
            return "";
        }
        return content.substring(index + 1).trim();
    }

    private String normalizeForMatch(String value) {
        String ascii = Normalizer.normalize(normalizeWhitespace(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return ascii.replaceAll("\\s+", " ").trim();
    }

    private String normalizeSlashCommandToken(String commandToken) {
        String normalized = normalizeLeadingSlash(stripZeroWidthChars(commandToken))
                .toLowerCase(Locale.ROOT)
                .trim();

        if (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        normalized = normalized.replaceAll("[,，。;；]+$", "");

        if (!normalized.startsWith("/") && normalized.startsWith("image")) {
            return "/" + normalized;
        }

        return normalized;
    }

    private String normalizeLeadingSlash(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text
                .replace('／', '/')
                .replace('⁄', '/')
                .replace('∕', '/');
    }

    private String normalizeWhitespace(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text
                .replace('\u00A0', ' ')
                .replaceAll("\\p{Z}+", " ");
    }

    private String stripZeroWidthChars(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private record QuickCommand(String key, String argument) {
    }
}
