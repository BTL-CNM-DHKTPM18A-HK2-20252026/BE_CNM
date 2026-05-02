package iuh.fit.service.ai.features.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.dto.response.ai.GoogleImageSearchResult;
import iuh.fit.dto.response.ai.GoogleWebSearchResult;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service tích hợp <b>Google Custom Search JSON API</b> để tìm kiếm thông tin
 * văn bản và hình ảnh thực tế từ internet.
 *
 * <h3>Cấu hình bắt buộc trong {@code application.yaml}:</h3>
 * 
 * <pre>{@code
 * google:
 *   api:
 *     key: ${GOOGLE_API_KEY}
 *   search:
 *     engine:
 *       id: ${GOOGLE_SEARCH_ENGINE_ID}
 * }</pre>
 *
 * <h3>Luồng hoạt động:</h3>
 * <ol>
 * <li>Validate từ khoá đầu vào.</li>
 * <li>Xây dựng URL an toàn bằng {@link UriComponentsBuilder}.</li>
 * <li>Gọi API qua {@link RestTemplate} (timeout 5s connect / 15s read).</li>
 * <li>Parse JSON, trả về danh sách DTO tương ứng.</li>
 * </ol>
 *
 * @see GoogleWebSearchResult
 * @see GoogleImageSearchResult
 */
@Slf4j
@Service
public class GoogleSearchService {

    // ── Constants ────────────────────────────────────────────────────────────

    private static final String SEARCH_API_URL = "https://www.googleapis.com/customsearch/v1";
    private static final int MAX_RESULTS = 10;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    // ── Config (injected from application.yaml) ──────────────────────────────

    /** Google Cloud API key — biến môi trường {@code GOOGLE_API_KEY}. */
    @Value("${google.api.key}")
    private String apiKey;

    /**
     * Custom Search Engine ID (CX) — biến môi trường
     * {@code GOOGLE_SEARCH_ENGINE_ID}.
     */
    @Value("${google.search.engine.id}")
    private String searchEngineId;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ObjectMapper objectMapper;

    public GoogleSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Tìm kiếm thông tin văn bản — tin tức, kết quả giải đấu, sự kiện, …
     *
     * @param query từ khoá tìm kiếm (non-blank)
     * @return danh sách tối đa {@value MAX_RESULTS} kết quả; empty list nếu không
     *         có kết quả
     * @throws AppException {@code INVALID_INPUT} nếu query rỗng;
     *                      {@code EXTERNAL_SERVICE_ERROR} nếu API lỗi / timeout
     */
    public List<GoogleWebSearchResult> searchInformation(String query) {
        validateQuery(query);

        String url = buildUrl(query, false);
        log.info("[GoogleSearch] Text search: '{}'", query);

        String body = callApi(url, query);
        return parseWebResults(body);
    }

    /**
     * Tìm kiếm hình ảnh thực tế từ internet.
     *
     * @param query từ khoá tìm kiếm (non-blank)
     * @return danh sách tối đa {@value MAX_RESULTS} kết quả ảnh với direct URL;
     *         empty list nếu không có kết quả
     * @throws AppException {@code INVALID_INPUT} nếu query rỗng;
     *                      {@code EXTERNAL_SERVICE_ERROR} nếu API lỗi / timeout
     */
    public List<GoogleImageSearchResult> searchImages(String query) {
        validateQuery(query);

        String url = buildUrl(query, true);
        log.info("[GoogleSearch] Image search: '{}'", query);

        String body = callApi(url, query);
        return parseImageResults(body);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Xây dựng URL an toàn — các tham số được encode tự động bởi
     * UriComponentsBuilder.
     * Không có nguy cơ URL injection từ query string người dùng.
     */
    private String buildUrl(String query, boolean imageSearch) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(SEARCH_API_URL)
                .queryParam("key", apiKey)
                .queryParam("cx", searchEngineId)
                .queryParam("q", query)
                .queryParam("num", MAX_RESULTS);

        if (imageSearch) {
            builder.queryParam("searchType", "image");
        }

        return builder.build().toUriString();
    }

    /**
     * Gọi Google Search API và trả về body JSON dạng String.
     * <ul>
     * <li>403 → thông báo rõ quota / API key.</li>
     * <li>4xx → lỗi phía client.</li>
     * <li>5xx → lỗi Google server.</li>
     * <li>Timeout / network → {@link ResourceAccessException}.</li>
     * </ul>
     */
    private String callApi(String url, String query) {
        RestTemplate restTemplate = buildRestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                        "Google Search API trả về HTTP " + response.getStatusCode());
            }

            return response.getBody();

        } catch (HttpClientErrorException ex) {
            log.error("[GoogleSearch] Client error {} khi tìm kiếm '{}': {}",
                    ex.getStatusCode(), query, ex.getResponseBodyAsString());

            String hint = (ex.getStatusCode().value() == 403)
                    ? " — API key không hợp lệ hoặc hạn mức (quota) đã hết."
                    : " — Yêu cầu không hợp lệ, kiểm tra lại API key / CX.";

            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Google Search API lỗi " + ex.getStatusCode() + hint);

        } catch (HttpServerErrorException ex) {
            log.error("[GoogleSearch] Server error {} khi tìm kiếm '{}': {}",
                    ex.getStatusCode(), query, ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Google Search API lỗi phía server (" + ex.getStatusCode() + "). Vui lòng thử lại.");

        } catch (ResourceAccessException ex) {
            log.error("[GoogleSearch] Timeout / network error khi tìm kiếm '{}': {}", query, ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Không thể kết nối đến Google Search API: " + ex.getMessage());
        }
    }

    /** Parse danh sách kết quả văn bản từ JSON trả về. */
    private List<GoogleWebSearchResult> parseWebResults(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");

            if (items.isMissingNode() || !items.isArray() || items.isEmpty()) {
                log.info("[GoogleSearch] Google không trả về kết quả văn bản nào.");
                return Collections.emptyList();
            }

            List<GoogleWebSearchResult> results = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                results.add(GoogleWebSearchResult.builder()
                        .title(item.path("title").asText(""))
                        .snippet(item.path("snippet").asText(""))
                        .link(item.path("link").asText(""))
                        .build());
            }

            log.debug("[GoogleSearch] Đã parse {} kết quả văn bản.", results.size());
            return results;

        } catch (Exception ex) {
            log.error("[GoogleSearch] Lỗi parse JSON kết quả văn bản: {}", ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Không thể xử lý phản hồi văn bản từ Google Search API.");
        }
    }

    /** Parse danh sách kết quả hình ảnh từ JSON trả về. */
    private List<GoogleImageSearchResult> parseImageResults(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");

            if (items.isMissingNode() || !items.isArray() || items.isEmpty()) {
                log.info("[GoogleSearch] Google không trả về kết quả hình ảnh nào.");
                return Collections.emptyList();
            }

            List<GoogleImageSearchResult> results = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                JsonNode img = item.path("image");
                results.add(GoogleImageSearchResult.builder()
                        .title(item.path("title").asText(""))
                        // Khi searchType=image, trường "link" chính là URL trực tiếp của ảnh
                        .imageUrl(item.path("link").asText(""))
                        .contextLink(img.path("contextLink").asText(""))
                        .thumbnailUrl(img.path("thumbnailLink").asText(""))
                        .build());
            }

            log.debug("[GoogleSearch] Đã parse {} kết quả hình ảnh.", results.size());
            return results;

        } catch (Exception ex) {
            log.error("[GoogleSearch] Lỗi parse JSON kết quả hình ảnh: {}", ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Không thể xử lý phản hồi hình ảnh từ Google Search API.");
        }
    }

    /** RestTemplate với timeout ngắn — tránh block thread quá lâu. */
    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /** Từ khoá tìm kiếm không được để trống hoặc chỉ có khoảng trắng. */
    private void validateQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "Từ khoá tìm kiếm (query) không được để trống.");
        }
    }
}
