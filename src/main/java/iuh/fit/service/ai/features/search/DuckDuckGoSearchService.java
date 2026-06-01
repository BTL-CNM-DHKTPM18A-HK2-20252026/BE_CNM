package iuh.fit.service.ai.features.search;

import iuh.fit.dto.response.ai.GoogleWebSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nguồn tìm kiếm web <b>dự phòng (fallback)</b> dựa trên DuckDuckGo HTML
 * endpoint ({@code https://html.duckduckgo.com/html/}).
 *
 * <p>
 * Khác với Google Custom Search JSON API (yêu cầu API key + billing),
 * DuckDuckGo
 * HTML <b>hoàn toàn miễn phí, không cần key, không giới hạn cứng</b> — phù hợp
 * làm phương án thay thế khi Google trả về lỗi 403 (quota/billing) hoặc không
 * có
 * kết quả.
 *
 * <p>
 * Trang HTML được parse bằng Jsoup; mỗi kết quả gồm tiêu đề
 * ({@code a.result__a}),
 * mô tả ({@code a.result__snippet}) và link gốc (giải mã từ tham số
 * {@code uddg}
 * trong URL redirect của DuckDuckGo).
 *
 * <p>
 * Mọi lỗi (timeout, mạng, đổi cấu trúc HTML) đều được nuốt và trả về danh sách
 * rỗng — đây là tính năng bổ trợ, không được làm hỏng luồng chat chính.
 *
 * @see GoogleWebSearchResult
 */
@Slf4j
@Service
public class DuckDuckGoSearchService {

    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /** Số kết quả tối đa lấy về. */
    @Value("${ai.fallback-search.max-results:10}")
    private int maxResults;

    /** Timeout (ms) cho mỗi request tới DuckDuckGo. */
    @Value("${ai.fallback-search.timeout-ms:10000}")
    private int timeoutMs;

    /**
     * Tìm kiếm thông tin văn bản qua DuckDuckGo HTML.
     *
     * @param query từ khoá tìm kiếm
     * @return danh sách kết quả (≤ {@code maxResults}); danh sách rỗng nếu lỗi /
     *         không có kết quả
     */
    public List<GoogleWebSearchResult> searchInformation(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        try {
            log.info("[DuckDuckGo] Fallback text search: '{}'", query);

            Document doc = Jsoup.connect(DDG_HTML_URL)
                    .userAgent(USER_AGENT)
                    .data("q", query)
                    .data("kl", "vn-vi") // ưu tiên kết quả Việt Nam
                    .timeout(timeoutMs)
                    .post();

            Elements resultBlocks = doc.select("div.result, div.web-result");
            if (resultBlocks.isEmpty()) {
                // Fallback selector nếu DDG đổi cấu trúc — duyệt trực tiếp link.
                resultBlocks = doc.select("div.results > div");
            }

            List<GoogleWebSearchResult> results = new ArrayList<>();
            for (Element block : resultBlocks) {
                Element titleEl = block.selectFirst("a.result__a");
                if (titleEl == null) {
                    continue;
                }

                String title = titleEl.text();
                String link = decodeDdgLink(titleEl.attr("href"));

                Element snippetEl = block.selectFirst("a.result__snippet, div.result__snippet");
                String snippet = snippetEl != null ? snippetEl.text() : "";

                if (!StringUtils.hasText(link)) {
                    continue;
                }

                results.add(GoogleWebSearchResult.builder()
                        .title(StringUtils.hasText(title) ? title : "(no title)")
                        .snippet(snippet)
                        .link(link)
                        .build());

                if (results.size() >= maxResults) {
                    break;
                }
            }

            log.info("[DuckDuckGo] Trả về {} kết quả cho: '{}'", results.size(), query);
            return results;

        } catch (Exception ex) {
            log.warn("[DuckDuckGo] Tìm kiếm thất bại cho '{}': {}", query, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Giải mã link gốc từ URL redirect của DuckDuckGo.
     *
     * <p>
     * DuckDuckGo bọc link dưới dạng
     * {@code //duckduckgo.com/l/?uddg=<URL_đã_encode>&rut=...}. Hàm này trích xuất
     * và giải mã tham số {@code uddg} để lấy URL gốc.
     *
     * @param href href thô lấy từ thẻ {@code a.result__a}
     * @return URL gốc đã giải mã, hoặc href chuẩn hoá nếu không có tham số uddg
     */
    private String decodeDdgLink(String href) {
        if (!StringUtils.hasText(href)) {
            return "";
        }

        // Chuẩn hoá link bắt đầu bằng "//" → thêm scheme.
        String normalized = href.startsWith("//") ? "https:" + href : href;

        int uddgIdx = normalized.indexOf("uddg=");
        if (uddgIdx < 0) {
            return normalized;
        }

        String encoded = normalized.substring(uddgIdx + "uddg=".length());
        int ampIdx = encoded.indexOf('&');
        if (ampIdx >= 0) {
            encoded = encoded.substring(0, ampIdx);
        }

        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return normalized;
        }
    }
}
