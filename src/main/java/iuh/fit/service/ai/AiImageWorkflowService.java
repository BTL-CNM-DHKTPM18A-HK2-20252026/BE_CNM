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

import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiImageWorkflowService {

    private static final String FALLBACK_IMAGE_PROVIDER_BASE = "https://image.pollinations.ai/prompt/";

    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final FileUploadRepository fileUploadRepository;

    @Value("${ai.image.provider-url:}")
    private String imageProviderUrl;

    @Value("${AI_IMAGE_API_KEY:}")
    private String imageApiKey;

    @Value("${ai.image.timeout-ms:45000}")
    private int timeoutMs;

    @Value("${ai.image.read-url-expiry-minutes:10080}")
    private long readUrlExpiryMinutes;

    public GeneratedImageResult generateAndStore(String userId, String description, String commandKey) {
        ImagePreset preset = resolvePreset(commandKey);
        byte[] imageBytes = generateImageBytes(description, preset);

        String safeName = buildSafeFileName(description);
        String storedFileName = UUID.randomUUID() + "_" + safeName + ".png";
        String objectKey = "users/" + userId + "/documents/generated_images/" + storedFileName;

        s3Service.uploadBytes(objectKey, imageBytes, "image/png");
        String readUrl = s3Service.generatePresignedReadUrl(objectKey, readUrlExpiryMinutes);

        fileUploadRepository.save(FileUpload.builder()
                .originalFileName(safeName + ".png")
                .storedFileName(storedFileName)
                .fileType(FileType.IMAGE)
                .mimeType("image/png")
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

        return new GeneratedImageResult(readUrl, storedFileName, imageBytes.length, preset.style);
    }

    private byte[] generateImageBytes(String description, ImagePreset preset) {
        RestTemplate restTemplate = buildRestTemplate();

        if (!StringUtils.hasText(imageProviderUrl)) {
            return generateByFallbackProvider(description, preset, restTemplate);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(imageApiKey)) {
            headers.setBearerAuth(imageApiKey);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", description);
        payload.put("style", preset.style);
        payload.put("size", preset.size);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(imageProviderUrl, request, String.class);

        return extractImageBytes(response.getBody(), restTemplate);
    }

    private byte[] generateByFallbackProvider(String description, ImagePreset preset, RestTemplate restTemplate) {
        try {
            String safePrompt = StringUtils.hasText(description)
                    ? URLEncoder.encode(description, java.nio.charset.StandardCharsets.UTF_8)
                    : URLEncoder.encode("beautiful landscape", java.nio.charset.StandardCharsets.UTF_8);

            String[] dimensions = preset.size.split("x");
            String width = dimensions.length > 0 ? dimensions[0] : "1024";
            String height = dimensions.length > 1 ? dimensions[1] : "1024";

            String url = FALLBACK_IMAGE_PROVIDER_BASE + safePrompt
                    + "?width=" + width
                    + "&height=" + height
                    + "&nologo=true";

            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
            byte[] data = response.getBody();
            if (data == null || data.length == 0) {
                throw new IllegalStateException("Fallback image provider returned empty data");
            }
            return data;
        } catch (Exception ex) {
            throw new IllegalStateException("Image provider is not configured and fallback generation failed", ex);
        }
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
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

    public record GeneratedImageResult(String imageUrl, String fileName, long fileSize, String style) {
    }
}
