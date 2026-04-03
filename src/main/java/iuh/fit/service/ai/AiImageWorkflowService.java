package iuh.fit.service.ai;

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
