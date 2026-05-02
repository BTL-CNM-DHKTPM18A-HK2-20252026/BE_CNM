package iuh.fit.service.ai.features.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.entity.FileUpload;
import iuh.fit.enums.FileType;
import iuh.fit.enums.StorageProvider;
import iuh.fit.repository.FileUploadRepository;
import iuh.fit.service.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiImageWorkflowService {

    private static final String FALLBACK_IMAGE_PROVIDER_BASE = "https://image.pollinations.ai/prompt/";
    private static final String OPENAI_IMAGE_URL = "https://api.openai.com/v1/images/generations";
    private static final String BLACKBOX_IMAGE_URL = "https://api.blackbox.ai/v1/images/generations";
    private static final String GEMINI_IMAGE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent";

    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final FileUploadRepository fileUploadRepository;

    @Value("${OPENAI_API_KEY:}")
    private String openaiApiKey;

    @Value("${BLACKBOX_API_KEY:}")
    private String blackboxApiKey;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${ai.image.timeout-ms:45000}")
    private int timeoutMs;

    @Value("${ai.image.read-url-expiry-minutes:10080}")
    private long readUrlExpiryMinutes;

    @Value("${ai.image.fallback.connect-timeout-ms:5000}")
    private int fallbackConnectTimeoutMs;

    @Value("${ai.image.fallback.read-timeout-ms:45000}")
    private int fallbackReadTimeoutMs;

    @Value("${ai.image.fallback.retries:4}")
    private int fallbackRetries;

    @Value("${ai.image.fallback.max-dimension:768}")
    private int fallbackMaxDimension;

    @Value("${ai.image.fallback.backoff-ms:1200}")
    private long fallbackBackoffMs;

    @Value("${ai.image.allow-local-placeholder:false}")
    private boolean allowLocalPlaceholder;

    public GeneratedImageResult generateAndStore(String userId, String description, String commandKey) {
        ImagePreset preset = resolvePreset(commandKey);
        byte[] imageBytes = generateImageBytes(description, preset);

        // Detect actual image format from magic bytes
        String mimeType = detectImageMime(imageBytes);
        String ext = mimeType.contains("webp") ? "webp" : "png";

        String safeName = buildSafeFileName(description);
        String storedFileName = UUID.randomUUID() + "_" + safeName + "." + ext;
        String objectKey = "users/" + userId + "/documents/generated_images/" + storedFileName;

        String readUrl;
        try {
            s3Service.uploadBytes(objectKey, imageBytes, mimeType);
            readUrl = s3Service.generatePresignedReadUrl(objectKey, readUrlExpiryMinutes);
        } catch (Exception ex) {
            log.warn("S3 upload failed for generated image, encoding as data URI: {}", ex.getMessage());
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            readUrl = "data:" + mimeType + ";base64," + base64;
        }

        try {
            fileUploadRepository.save(FileUpload.builder()
                    .originalFileName(safeName + "." + ext)
                    .storedFileName(storedFileName)
                    .fileType(FileType.IMAGE)
                    .mimeType(mimeType)
                    .fileSize((long) imageBytes.length)
                    .fileUrl(readUrl)
                    .thumbnailUrl(readUrl)
                    .storageProvider(StorageProvider.AWS_S3)
                    .resourceId(objectKey)
                    .folderPath("documents/generated_images")
                    .uploadedBy(userId)
                    .uploadedAt(LocalDateTime.now())
                    .description("AI generated image")
                    .status("ACTIVE")
                    .metadata("{\"classification\":\"PERSONAL_DOCUMENT\",\"folder\":\"Generated Images\",\"preset\":\""
                            + preset.key + "\",\"style\":\"" + preset.style + "\"}")
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to save file upload record: {}", ex.getMessage());
        }

        return new GeneratedImageResult(readUrl, storedFileName, imageBytes.length, preset.style);
    }

    private byte[] generateImageBytes(String description, ImagePreset preset) {
        PreparedPrompt preparedPrompt = preparePrompt(description, preset);
        RestTemplate restTemplate = buildRestTemplate();

        // 1) OpenAI DALL-E
        if (StringUtils.hasText(openaiApiKey)) {
            try {
                byte[] result = generateByOpenAi(preparedPrompt.prompt(), preset);
                if (result != null && result.length > 0)
                    return result;
            } catch (Exception ex) {
                log.warn("[IMAGE_GEN] OpenAI FAILED: {}", ex.getMessage());
            }
        }

        // 2) Blackbox AI
        if (StringUtils.hasText(blackboxApiKey)) {
            try {
                byte[] result = generateByBlackbox(preparedPrompt.prompt(), preset);
                if (result != null && result.length > 0)
                    return result;
            } catch (Exception ex) {
                log.warn("[IMAGE_GEN] Blackbox FAILED: {}", ex.getMessage());
            }
        }

        // 3) Gemini
        if (StringUtils.hasText(geminiApiKey)) {
            try {
                byte[] result = generateByGemini(preparedPrompt.prompt());
                if (result != null && result.length > 0)
                    return result;
            } catch (Exception ex) {
                log.warn("[IMAGE_GEN] Gemini FAILED: {}", ex.getMessage());
            }
        }

        // 4) Fallback: pollinations.ai
        return generateByFallbackProvider(preparedPrompt.prompt(), preset, restTemplate);
    }

    private byte[] generateByOpenAi(String prompt, ImagePreset preset) throws Exception {
        RestTemplate restTemplate = buildRestTemplate(12000, 60000);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        String size = resolveOpenAiSize(preset.size);
        String trimmedPrompt = prompt.length() > 4000 ? prompt.substring(0, 4000) : prompt;

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "dall-e-3");
        payload.put("prompt", trimmedPrompt);
        payload.put("n", 1);
        payload.put("size", size);
        payload.put("response_format", "b64_json");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_IMAGE_URL, request, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String b64 = pickFirstText(root, "/data/0/b64_json");
        if (StringUtils.hasText(b64)) {
            return Base64.getDecoder().decode(b64);
        }

        String url = pickFirstText(root, "/data/0/url");
        if (StringUtils.hasText(url)) {
            RestTemplate dlTemplate = buildRestTemplate(8000, 30000);
            ResponseEntity<byte[]> imgResp = dlTemplate.getForEntity(url, byte[].class);
            byte[] data = imgResp.getBody();
            if (data != null && data.length > 0) {
                return data;
            }
        }

        throw new IllegalStateException("OpenAI returned no image data");
    }

    private byte[] generateByBlackbox(String prompt, ImagePreset preset) throws Exception {
        RestTemplate restTemplate = buildRestTemplate(12000, 60000);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(blackboxApiKey);

        String trimmedPrompt = prompt.length() > 500 ? prompt.substring(0, 500) : prompt;
        String size = resolveOpenAiSize(preset.size);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "flux-pro");
        payload.put("prompt", trimmedPrompt);
        payload.put("n", 1);
        payload.put("size", size);
        payload.put("response_format", "b64_json");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(BLACKBOX_IMAGE_URL, request, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String b64 = pickFirstText(root, "/data/0/b64_json");
        if (StringUtils.hasText(b64))
            return Base64.getDecoder().decode(b64);

        String url = pickFirstText(root, "/data/0/url");
        if (StringUtils.hasText(url)) {
            ResponseEntity<byte[]> imgResp = buildRestTemplate(8000, 30000).getForEntity(url, byte[].class);
            if (imgResp.getBody() != null && imgResp.getBody().length > 0)
                return imgResp.getBody();
        }

        throw new IllegalStateException("Blackbox returned no image data");
    }

    private byte[] generateByGemini(String prompt) throws Exception {
        RestTemplate restTemplate = buildRestTemplate(12000, 60000);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt.length() > 500 ? prompt.substring(0, 500) : prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseModalities", List.of("IMAGE", "TEXT"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", List.of(content));
        payload.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                GEMINI_IMAGE_URL + "?key=" + geminiApiKey, request, String.class);

        JsonNode partsNode = objectMapper.readTree(response.getBody()).at("/candidates/0/content/parts");
        if (partsNode.isArray()) {
            for (JsonNode part : partsNode) {
                String b64 = part.at("/inlineData/data").asText(null);
                if (StringUtils.hasText(b64))
                    return Base64.getDecoder().decode(b64);
            }
        }

        throw new IllegalStateException("Gemini returned no image data");
    }

    /**
     * Analyzes an image at the given URL using Gemini Vision and returns a text
     * description.
     *
     * @param imageUrl   publicly-accessible URL of the image (e.g. S3 presigned
     *                   URL)
     * @param userPrompt optional user question/prompt about the image; falls back
     *                   to a generic description prompt
     * @param language   "vi" or "en"
     * @return text description from Gemini, or null if unavailable
     */
    public String analyzeImageWithGemini(String imageUrl, String userPrompt, String language) {
        if (!StringUtils.hasText(geminiApiKey) || !StringUtils.hasText(imageUrl)) {
            return null;
        }
        try {
            // Download via S3 SDK directly — no presigned URL needed, SDK handles IAM auth.
            byte[] imageBytes = downloadImageBytes(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("[VISION] Downloaded image is empty from URL: {}", imageUrl);
                return null;
            }

            String mimeType = detectImageMime(imageBytes);
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);

            String prompt;
            if (StringUtils.hasText(userPrompt)) {
                prompt = userPrompt.length() > 1000 ? userPrompt.substring(0, 1000) : userPrompt;
            } else {
                prompt = "vi".equals(language)
                        ? "Hãy mô tả chi tiết nội dung trong ảnh này."
                        : "Describe the content of this image in detail.";
            }

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Data);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("inlineData", inlineData);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(textPart, imagePart));

            Map<String, Object> payload = new HashMap<>();
            payload.put("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            String visionUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
            RestTemplate restTemplate = buildRestTemplate(12000, 40000);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    visionUrl + "?key=" + geminiApiKey, request, String.class);

            JsonNode partsNode = objectMapper.readTree(response.getBody()).at("/candidates/0/content/parts");
            if (partsNode.isArray()) {
                for (JsonNode part : partsNode) {
                    String text = part.path("text").asText(null);
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
            }
            log.warn("[VISION] Gemini returned no text for image analysis");
            return null;
        } catch (Exception e) {
            log.warn("[VISION] Failed to analyze image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Analyze an image using OpenAI GPT-4o vision with Chain-of-Thought reasoning
     * and a strict 85% confidence gate to minimize hallucination.
     *
     * Strategy:
     * 1. Step 1 (Observe): Force the model to list raw visual evidence first
     * (colors, shapes, text, art style, UI elements) — no conclusions yet.
     * 2. Step 2 (Reason): Ask the model to derive its answer from the evidence
     * and self-assess confidence (0–100).
     * 3. Gate: If confidence < 85 or the model says "không chắc", return a
     * specific low-confidence marker so AiChatService can ask for more context
     * rather than hallucinating a confident-sounding wrong answer.
     *
     * @param imageUrl   URL of the image (S3 presigned or public)
     * @param userPrompt optional user question about the image
     * @param language   "vi" or "en"
     * @return structured vision analysis string, or null if unavailable
     */
    public String analyzeImageWithOpenAi(String imageUrl, String userPrompt, String language) {
        if (!StringUtils.hasText(openaiApiKey) || !StringUtils.hasText(imageUrl)) {
            return null;
        }
        try {
            // Download via S3 SDK directly — no presigned URL needed, SDK handles IAM auth.
            byte[] imageBytes = downloadImageBytes(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("[VISION-OAI] Downloaded image is empty from URL: {}", imageUrl);
                return null;
            }

            String mimeType = detectImageMime(imageBytes);
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            String dataUri = "data:" + mimeType + ";base64," + base64Data;

            boolean isVi = "vi".equals(language);
            String userQuestion = StringUtils.hasText(userPrompt)
                    ? (userPrompt.length() > 800 ? userPrompt.substring(0, 800) : userPrompt)
                    : (isVi ? "Hãy mô tả chi tiết nội dung trong ảnh này."
                            : "Describe the content of this image in detail.");

            // ── System prompt: Visual Chain-of-Thought (VCoT) 4-step + Valorant-priority +
            // Pengu disambiguation
            // ──
            String systemPrompt = isVi
                    ? """
                            Bạn là chuyên gia nhận diện nhân vật game/anime với kỹ năng phân tích hình ảnh Visual Chain of Thought.
                            Khi được cung cấp một hình ảnh, thực hiện ĐÚNG 4 bước sau:

                            BƯỚC 1 — NHẬN DẠNG ĐẶC ĐIỂM (Raw Evidence):
                            Liệt kê cụ thể:
                            - Màu tóc + kiểu tóc
                            - Màu mắt + phụ kiện mắt (có đeo mặt nạ mắt không? màu gì?)
                            - Trang phục: màu sắc chính, chi tiết nổi bật, logo/biểu tượng
                            - Phụ kiện đặc trưng (vũ khí, mũ, khăn…)
                            - Sinh vật/pet đi kèm — mô tả cụ thể hình dạng của pet (đây là dấu hiệu quan trọng)
                            - Văn bản / logo xuất hiện trong ảnh
                            - Phong cách nghệ thuật: chibi, realistic, anime, pixel art…
                            - Màu nền

                            BƯỚC 2 — ĐỐI CHIẾU DATABASE (Thứ tự ưu tiên bắt buộc):
                            Hãy kiểm tra theo ĐÚNG thứ tự này:

                            [1] VALORANT trước — Xét agent Valorant trước tiên:
                              Sage = tóc đen dài, trang phục xanh ngọc/xẹo
                              Reyna = tóc đen/tím, hai dải tóc nổi bật, mắt tím
                              Sova = tóc vàng, mặt nạ mắt màu xanh dương
                              Neon = tóc vàng, áo jacket xanh dương nổi
                              Jett = tóc xanh lá ngắn
                              Viper = tóc đen, áo chống độc xanh lá
                              Killjoy = tóc vàng, áo vàng/cam
                              Chamber = tóc nâu sáng, vest đen lịch
                              Gekko = tóc xanh lá nổi, companion Wingman/Dizzy/Thrash/Mosh

                            [2] LEAGUE OF LEGENDS tiếp theo

                            [3] Genshin Impact, Overwatch, Honkai, Blue Archive, One Piece, Naruto, MHA… sau cùng

                            QUY TẮC PENGU — BẮT BUỘC TUÂN THỦ:
                            Nếu ảnh có linh vật Pengu (con chò mở chiết đầu vuông/tròn trơn, đơn giản), ĐÓ LÀ KHÔNG ĐỦ ĐỂ KẾT LUẬN LÀ LEAGUE OF LEGENDS.
                            Pengu xuất hiện trong cả League of Legends LÀ CẢ Valorant (scoins Pengu Cosplay). Cần xác định game dựa vào NHÂN VẬT CHÍNH, không dựa vào Pengu.

                            BƯỚC 3 — DỰ ĐOÁN:
                            - Nếu CHIBI: không dựa vào tỷ lệ cơ thể. Tập trung: màu tóc, vũ khí, trang phục, pet.
                            - Đưa ra: Tên nhân vật + Tựa game/anime
                            - Độ tự tin 0–100

                            BƯỚC 4 — QUY TẮC ĐỘ TỰ TIN:
                            - ≥ 70%: Khẳng định trực tiếp
                            - < 70%: Liệt kê 2–3 phương án: "Có thể là: [A] trong [Game A] (~X%), [B] trong [Game B] (~Y%)"
                            - KHÔNG bao giờ trả lời rỗng

                            Trả lời bằng tiếng Việt.
                            """
                    : """
                            You are an expert game/anime character identifier with Visual Chain of Thought (VCoT) image analysis.
                            When given an image, perform EXACTLY these 4 steps:

                            STEP 1 — IDENTIFY VISUAL FEATURES (Raw Evidence):
                            List specifically:
                            - Hair color + hairstyle
                            - Eye color and any eye accessories (eye patch? color?)
                            - Outfit: main colors, distinctive details, logos/symbols
                            - Signature accessories (weapons, scarves, masks…)
                            - Companion creatures/pets — describe the pet's specific shape and design
                            - Visible text / logos in the image
                            - Art style: chibi, realistic, anime 2D, pixel art…
                            - Background color

                            STEP 2 — CROSS-REFERENCE DATABASE (mandatory evaluation order):
                            You MUST evaluate in this exact order:

                            [PRIORITY 1] VALORANT Agents FIRST — check before anything else:
                              Sage       = long black hair, jade/teal green outfit
                              Reyna      = black/purple hair, two prominent hair strands, purple eyes
                              Sova       = blonde hair, blue eye patch over right eye
                              Neon       = blonde hair, bright blue jacket
                              Jett       = short light blue-green hair
                              Viper      = black hair, green hazmat-style coat
                              Killjoy    = blonde hair, yellow/orange outfit, glasses
                              Chamber    = light brown hair, sharp dark suit
                              Gekko      = bright green hair, companions: Wingman/Dizzy/Thrash/Mosh

                            [PRIORITY 2] LEAGUE OF LEGENDS Champions (evaluate only after ruling out Valorant)

                            [PRIORITY 3] Genshin Impact, Overwatch, Honkai Star Rail, Blue Archive, Naruto, One Piece, MHA, etc.

                            PENGU RULE — MANDATORY:
                            If the image contains a Pengu mascot (small round/square-headed penguin-like creature, simple design),
                            this does NOT mean the game is League of Legends.
                            Pengu appears in BOTH League of Legends AND Valorant ("Pengu Cosplay" skins exist in Valorant).
                            Identify the game by the MAIN CHARACTER's features, NOT by the presence of Pengu.

                            STEP 3 — MAKE PREDICTION:
                            - For CHIBI art style: DO NOT rely on body proportions. Focus on: hair color, weapon, outfit details, pet/companion.
                            - State: character name + game/anime title
                            - Self-assess confidence 0–100

                            STEP 4 — CONFIDENCE RULES:
                            - ≥ 70%: State result directly and confidently
                            - < 70%: MUST list 2–3 candidates: "Possible: [Name A] from [Game A] (~X%), [Name B] from [Game B] (~Y%)"
                            - NEVER give an empty or unhelpful response

                            Respond in English.
                            """;

            // Build the request: system prompt + user question + image
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);

            Map<String, Object> imageUrlPart = new HashMap<>();
            imageUrlPart.put("url", dataUri);
            imageUrlPart.put("detail", "high"); // High-res tile processing
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrlPart);
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", userQuestion);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", List.of(imagePart, textPart));

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-4o");
            payload.put("messages", List.of(systemMsg, userMsg));
            payload.put("max_tokens", 1000);
            payload.put("temperature", 0.0); // Zero temperature = maximum factual grounding, no creative guessing
            payload.put("top_p", 0.1); // Tight nucleus sampling to prevent hallucination

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            RestTemplate restTemplate = buildRestTemplate(15000, 55000);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions", request, String.class);

            JsonNode choices = objectMapper.readTree(response.getBody()).path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String text = choices.get(0).at("/message/content").asText(null);
                if (StringUtils.hasText(text)) {
                    log.debug("[VISION-OAI] Analysis complete ({} chars)", text.length());
                    return text;
                }
            }
            log.warn("[VISION-OAI] OpenAI returned no text for image analysis");
            return null;
        } catch (Exception e) {
            log.warn("[VISION-OAI] Failed to analyze image with OpenAI: {}", e.getMessage());
            return null;
        }
    }

    private String resolveOpenAiSize(String presetSize) {
        if (!StringUtils.hasText(presetSize))
            return "1024x1024";
        int w = parseDimension(presetSize, 0, 1024);
        int h = parseDimension(presetSize, 1, 1024);
        // Map to nearest supported DALL-E 3 size
        if (w > h)
            return "1792x1024";
        if (h > w)
            return "1024x1792";
        return "1024x1024";
    }

    private PreparedPrompt preparePrompt(String originalDescription, ImagePreset preset) {
        String baseDescription = StringUtils.hasText(originalDescription)
                ? originalDescription.trim()
                : "beautiful nature scene";
        String lowered = normalizeForDetect(baseDescription);
        String coreDescription = stripLeadingImageVerb(baseDescription, lowered);
        if (!StringUtils.hasText(coreDescription)) {
            coreDescription = baseDescription;
        }
        String coreLowered = normalizeForDetect(coreDescription);

        String subject = detectSubject(coreLowered);
        String styleHint = switch (preset.key) {
            case "image.pro" -> "highly detailed, 4k, sharp focus";
            case "image.sketch" -> "black and white sketch, clean line art";
            case "image.wallpaper" -> "cinematic composition, wallpaper style";
            default -> "natural proportions, clean composition";
        };

        String prompt;
        if ("bird".equals(subject)) {
            boolean wantsFlying = detectFlightIntent(coreLowered);
            boolean wantsPerched = detectPerchedIntent(coreLowered);

            String poseHint;
            if (wantsFlying && !wantsPerched) {
                poseHint = "Bird pose: flying in the air, wings spread naturally, clear motion posture";
            } else if (wantsPerched && !wantsFlying) {
                poseHint = "Bird pose: perched naturally on a tree branch";
            } else {
                poseHint = "Bird pose: natural posture according to user request";
            }

            prompt = "Primary subject: ONE bird (avian animal), not a person, not a mammal. "
                    + "Generate exactly one realistic bird as the central subject, full body visible. "
                    + "No humans, no humanoids, no cows, no bulls, no hybrid creatures, no toy-like creature. "
                    + poseHint + ". "
                    + "User request details: " + coreDescription + ". "
                    + "Bird anatomy must be correct: one beak, two wings, two legs, natural feather structure. "
                    + "Avoid deformation, avoid extra limbs, avoid duplicate heads, avoid distorted beak. "
                    + "Use realistic feather texture and consistent natural bird colors unless user requests otherwise. "
                    + "Style: " + styleHint + ". "
                    + "High subject fidelity required.";
        } else if ("cat".equals(subject)) {
            prompt = "Primary subject: a cat. "
                    + "Generate exactly one cat as the main focus. "
                    + "Do not generate humans or mixed creatures. "
                    + "User request details: " + coreDescription + ". "
                    + "Style: " + styleHint + ".";
        } else if ("dog".equals(subject)) {
            prompt = "Primary subject: a dog. "
                    + "Generate exactly one dog as the main focus. "
                    + "Do not generate humans or mixed creatures. "
                    + "User request details: " + coreDescription + ". "
                    + "Style: " + styleHint + ".";
        } else {
            prompt = "Generate an image that follows this request exactly: " + coreDescription + ". "
                    + "Keep one clear primary subject, avoid mixed-creature artifacts. "
                    + "Style: " + styleHint + ".";
        }

        return new PreparedPrompt(prompt, subject);
    }

    private byte[] generateByFallbackProvider(String description, ImagePreset preset, RestTemplate restTemplate) {
        RestTemplate fastFallbackTemplate = buildRestTemplate(fallbackConnectTimeoutMs, fallbackReadTimeoutMs);

        try {
            String safePrompt = StringUtils.hasText(description)
                    ? URLEncoder.encode(description, java.nio.charset.StandardCharsets.UTF_8)
                    : URLEncoder.encode("beautiful landscape", java.nio.charset.StandardCharsets.UTF_8);

            int requestedWidth = parseDimension(preset.size, 0, 1024);
            int requestedHeight = parseDimension(preset.size, 1, 1024);
            int[] scaledDimensions = scaleDownDimensions(requestedWidth, requestedHeight,
                    Math.max(256, fallbackMaxDimension));
            int width = scaledDimensions[0];
            int height = scaledDimensions[1];

            int attempts = Math.max(1, fallbackRetries + 1);
            Exception lastError = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    long baseSeed = Integer.toUnsignedLong(description.hashCode());
                    if (baseSeed == 0) {
                        baseSeed = 1;
                    }
                    long seed = baseSeed + attempt - 1;

                    List<String> candidateUrls = buildFallbackCandidateUrls(safePrompt, width, height, seed);
                    HttpHeaders requestHeaders = new HttpHeaders();
                    requestHeaders.set("User-Agent", "Fruvia-AI-Image/1.0");
                    HttpEntity<Void> requestEntity = new HttpEntity<>(requestHeaders);

                    for (String url : candidateUrls) {
                        try {
                            ResponseEntity<byte[]> response = fastFallbackTemplate.exchange(url, HttpMethod.GET,
                                    requestEntity, byte[].class);
                            byte[] data = response.getBody();
                            if (data != null && data.length > 0) {
                                return data;
                            }
                        } catch (Exception ex) {
                            lastError = ex;
                            log.warn("Fallback image URL variant failed (attempt {}/{}): {}", attempt, attempts,
                                    ex.getMessage());
                        }
                    }

                    throw new IllegalStateException("Fallback image provider returned empty data");
                } catch (Exception ex) {
                    lastError = ex;
                    log.warn("Fallback image attempt {}/{} failed: {}", attempt, attempts, ex.getMessage());

                    if (attempt < attempts && fallbackBackoffMs > 0) {
                        sleepQuietly(fallbackBackoffMs * attempt);
                    }
                }
            }

            throw new IllegalStateException("All fallback image attempts failed", lastError);
        } catch (Exception ex) {
            if (allowLocalPlaceholder) {
                log.warn("Remote fallback image provider failed: {}. Switching to local placeholder image.",
                        ex.getMessage());
                return generateLocalPlaceholderImage(description, preset);
            }

            throw new IllegalStateException(
                    "Image provider unavailable (remote fallback failed and local placeholder disabled)", ex);
        }
    }

    private String normalizeForDetect(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String stripLeadingImageVerb(String original, String loweredNormalized) {
        String[][] prefixes = new String[][] {
                { "tao hinh anh ", "3" },
                { "tao anh ", "2" },
                { "ve hinh ", "2" },
                { "ve ", "1" },
                { "generate image ", "2" },
                { "create image ", "2" },
                { "draw ", "1" }
        };

        for (String[] prefix : prefixes) {
            if (loweredNormalized.startsWith(prefix[0])) {
                int skipTokens = Integer.parseInt(prefix[1]);
                String[] tokens = original.trim().split("\\s+");
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
        }

        return original;
    }

    private String detectSubject(String loweredNormalized) {
        if (containsAny(loweredNormalized, " chim ", " con chim", " bird ", " sparrow", " eagle", " parrot",
                " owl", " avian")) {
            return "bird";
        }
        if (containsAny(loweredNormalized, " meo", " con meo", " cat ", " kitten", " kitty")) {
            return "cat";
        }
        if (containsAny(loweredNormalized, " cho ", " con cho", " dog ", " puppy")) {
            return "dog";
        }
        return "generic";
    }

    private boolean detectFlightIntent(String loweredNormalized) {
        return containsAny(loweredNormalized,
                " dang bay ",
                " chim dang bay",
                " bay tren troi",
                " canh dang mo",
                " canh mo",
                " flying ",
                " in flight",
                " soaring",
                " gliding");
    }

    private boolean detectPerchedIntent(String loweredNormalized) {
        return containsAny(loweredNormalized,
                " dau tren canh cay",
                " tren canh cay",
                " perched ",
                " perched on",
                " on a branch",
                " on tree branch");
    }

    private boolean containsAny(String text, String... keywords) {
        String padded = " " + text + " ";
        for (String keyword : keywords) {
            if (padded.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private byte[] generateLocalPlaceholderImage(String description, ImagePreset preset) {
        int width = parseDimension(preset.size, 0, 1024);
        int height = parseDimension(preset.size, 1, 1024);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setPaint(new GradientPaint(0, 0, new Color(36, 99, 235), width, height, new Color(13, 148, 136)));
            g.fillRect(0, 0, width, height);

            int panelX = Math.max(24, width / 20);
            int panelY = Math.max(24, height / 10);
            int panelW = width - panelX * 2;
            int panelH = height - panelY * 2;

            g.setColor(new Color(255, 255, 255, 220));
            g.fillRoundRect(panelX, panelY, panelW, panelH, 24, 24);

            g.setColor(new Color(17, 24, 39));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(20, width / 36)));
            int cursorY = panelY + Math.max(42, height / 11);
            g.drawString("AI Image Placeholder", panelX + 24, cursorY);

            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(14, width / 64)));
            cursorY += Math.max(28, height / 20);
            g.drawString("Network image provider unavailable, generated local fallback.", panelX + 24, cursorY);

            cursorY += Math.max(30, height / 18);
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(15, width / 60)));
            g.drawString("Prompt:", panelX + 24, cursorY);

            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(14, width / 64)));
            List<String> lines = wrapText(StringUtils.hasText(description) ? description : "(empty prompt)", 64);
            int lineHeight = Math.max(20, height / 28);
            int maxLines = Math.max(3, (panelH - (cursorY - panelY) - 24) / lineHeight);
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                cursorY += lineHeight;
                g.drawString(lines.get(i), panelX + 24, cursorY);
            }

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                return output.toByteArray();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create local placeholder image", ex);
        } finally {
            g.dispose();
        }
    }

    private int parseDimension(String size, int index, int defaultValue) {
        if (!StringUtils.hasText(size)) {
            return defaultValue;
        }
        String[] dimensions = size.split("x");
        if (dimensions.length <= index) {
            return defaultValue;
        }
        try {
            return Math.max(256, Integer.parseInt(dimensions[index]));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int[] scaleDownDimensions(int width, int height, int maxDimension) {
        if (width <= maxDimension && height <= maxDimension) {
            return new int[] { width, height };
        }

        double scale = Math.min((double) maxDimension / Math.max(1, width),
                (double) maxDimension / Math.max(1, height));

        int scaledWidth = Math.max(256, (int) Math.round(width * scale));
        int scaledHeight = Math.max(256, (int) Math.round(height * scale));
        return new int[] { scaledWidth, scaledHeight };
    }

    private List<String> buildFallbackCandidateUrls(String safePrompt, int width, int height, long seed) {
        // Keep parameters minimal to avoid 502 errors from pollinations.ai
        String base = FALLBACK_IMAGE_PROVIDER_BASE + safePrompt
                + "?width=" + width
                + "&height=" + height
                + "&nologo=true"
                + "&seed=" + seed;

        List<String> urls = new ArrayList<>();
        // Try without model first (most reliable), then with specific models
        urls.add(base);
        urls.add(base + "&model=flux");
        urls.add(base + "&model=turbo");
        urls.add(base);
        urls.add(base + "&model=turbo");
        return urls;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(1, millis));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            lines.add("");
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            if (line.length() + 1 + word.length() <= maxChars) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private byte[] extractImageBytes(String responseBody, RestTemplate restTemplate) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String directUrl = pickFirstText(root,
                    "/imageUrl",
                    "/url",
                    "/data/0/url",
                    "/images/0/url");
            if (StringUtils.hasText(directUrl)) {
                ResponseEntity<byte[]> bytesResponse = restTemplate.getForEntity(directUrl, byte[].class);
                byte[] data = bytesResponse.getBody();
                if (data != null && data.length > 0) {
                    return data;
                }
            }

            String base64 = pickFirstText(root,
                    "/b64_json",
                    "/base64",
                    "/imageBase64",
                    "/data/0/b64_json",
                    "/images/0/b64_json");
            if (StringUtils.hasText(base64)) {
                return Base64.getDecoder().decode(base64);
            }
        } catch (Exception ex) {
            log.error("Failed to parse image provider response: {}", ex.getMessage());
            throw new IllegalStateException("Failed to parse image provider response", ex);
        }

        throw new IllegalStateException("Image provider returned no usable image data");
    }

    /**
     * Downloads image bytes for vision analysis.
     * For S3 URLs: uses S3 SDK getObject() directly (IAM auth, no presigned URL
     * needed).
     * For external URLs: falls back to RestTemplate GET.
     */
    private byte[] downloadImageBytes(String imageUrl) throws Exception {
        if (!StringUtils.hasText(imageUrl))
            return null;
        // Extract S3 object key: everything after ".com/", strip query params
        int idx = imageUrl.indexOf(".com/");
        if (idx != -1) {
            String objectKey = imageUrl.substring(idx + 5);
            int qIdx = objectKey.indexOf('?');
            if (qIdx != -1)
                objectKey = objectKey.substring(0, qIdx);
            objectKey = java.net.URLDecoder.decode(objectKey, java.nio.charset.StandardCharsets.UTF_8);
            log.debug("[VISION] Downloading via S3 SDK: {}", objectKey);
            return s3Service.downloadBytes(objectKey);
        }
        // Non-S3 URL (e.g., external image) — plain HTTP GET
        log.debug("[VISION] Downloading via HTTP (non-S3 URL): {}", imageUrl);
        RestTemplate dlTemplate = buildRestTemplate(8000, 30000);
        ResponseEntity<byte[]> resp = dlTemplate.getForEntity(imageUrl, byte[].class);
        return resp.getBody();
    }

    private RestTemplate buildRestTemplate() {
        return buildRestTemplate(timeoutMs, timeoutMs);
    }

    private RestTemplate buildRestTemplate(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, connectTimeout));
        factory.setReadTimeout(Math.max(1000, readTimeout));
        return new RestTemplate(factory);
    }

    private String pickFirstText(JsonNode root, String... jsonPointers) {
        for (String pointer : jsonPointers) {
            JsonNode node = root.at(pointer);
            if (!node.isMissingNode() && !node.isNull()) {
                String value = node.asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private ImagePreset resolvePreset(String commandKey) {
        return switch (commandKey) {
            case "image.pro" -> new ImagePreset("image.pro", "digital-4k", "1792x1024");
            case "image.sketch" -> new ImagePreset("image.sketch", "black-and-white-sketch", "1024x1024");
            case "image.wallpaper" -> new ImagePreset("image.wallpaper", "wallpaper", "1536x1024");
            default -> new ImagePreset("image.generate", "photorealistic", "1024x1024");
        };
    }

    private String detectImageMime(byte[] data) {
        if (data != null && data.length >= 12) {
            // WebP: starts with RIFF....WEBP
            if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
                return "image/webp";
            }
            // JPEG: starts with FF D8 FF
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
        }
        return "image/png";
    }

    private String buildSafeFileName(String description) {
        if (!StringUtils.hasText(description)) {
            return "generated_image";
        }

        String normalized = Normalizer.normalize(description, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s_-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();

        if (!StringUtils.hasText(normalized)) {
            return "generated_image";
        }

        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    private record ImagePreset(String key, String style, String size) {
    }

    private record PreparedPrompt(String prompt, String subject) {
    }

    public record GeneratedImageResult(String imageUrl, String fileName, long fileSize, String style) {
    }
}
