package iuh.fit.service.ai.features.search;

import iuh.fit.dto.response.ai.GoogleWebSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry các <b>nguồn dữ liệu số thời gian thực</b> (data-rich sources) dùng
 * chung cho nhiều chủ đề: giá vàng, tỷ giá, crypto, …
 *
 * <p>
 * Đây là giải pháp <i>generic, registry-driven</i> — KHÔNG phải service riêng
 * cho từng chủ đề. Mỗi chủ đề được mô tả bằng một {@link TopicSources} (pattern
 * nhận diện + danh sách nguồn). Khi câu hỏi của người dùng khớp pattern, registry
 * tự động fetch các nguồn tương ứng (JSON API hoặc HTML tĩnh), trích phần chứa
 * con số thật và trả về dưới dạng {@link GoogleWebSearchResult} để tiêm vào
 * context của AI.
 *
 * <p>
 * Lý do tồn tại: các trang giá phổ biến (24h, pnj, giavang…) render số bằng
 * JavaScript nên Jsoup tĩnh không đọc được. Registry này nhắm tới các nguồn
 * <b>có số trong HTML tĩnh</b> hoặc <b>JSON API công khai</b> để luôn lấy được
 * con số thật, kể cả khi Google Custom Search bị lỗi/quota.
 */
@Service
@Slf4j
public class RealtimeDataSourceRegistry {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /** Timeout (ms) cho mỗi lần fetch nguồn dữ liệu. */
    @Value("${ai.realtime-data.timeout-ms:8000}")
    private int timeoutMs;

    /** Giới hạn ký tự trích từ mỗi nguồn (tránh vượt token budget). */
    @Value("${ai.realtime-data.max-chars-per-source:1800}")
    private int maxCharsPerSource;

    /** Loại nguồn: JSON API hay trang HTML tĩnh. */
    private enum SourceType {
        JSON, HTML
    }

    /** Một nguồn dữ liệu cụ thể. */
    private record DataSource(String label, String url, SourceType type) {
    }

    /** Một nhóm chủ đề: pattern nhận diện + các nguồn dữ liệu. */
    private record TopicSources(Pattern pattern, List<DataSource> sources) {
    }

    /**
     * Bảng đăng ký chủ đề → nguồn dữ liệu. Thêm chủ đề mới chỉ cần thêm một
     * {@link TopicSources} vào đây (không phải viết service mới).
     */
    private static final List<TopicSources> REGISTRY = List.of(
            // ── Giá vàng ──
            new TopicSources(
                    Pattern.compile("\\b(giá vàng|gold price|vàng sjc|vàng pnj|vàng nhẫn|btmc|giá vàng hôm nay)\\b",
                            Pattern.CASE_INSENSITIVE),
                    List.of(
                            new DataSource("BTMC - Giá vàng (JSON API)",
                                    "http://api.btmc.vn/api/BTMCAPI/getpricebtmc?key=3kd8ub1llcg9t45hnoh8hmn7t5kc2v",
                                    SourceType.JSON),
                            new DataSource("Webgia - Giá vàng",
                                    "https://webgia.com/gia-vang/",
                                    SourceType.HTML))),
            // ── Tỷ giá ngoại tệ ──
            new TopicSources(
                    Pattern.compile("\\b(tỷ giá|exchange rate|usd/?vnd|usd sang vnd|quy đổi (usd|euro|eur))\\b",
                            Pattern.CASE_INSENSITIVE),
                    List.of(
                            new DataSource("ExchangeRate API (USD base, JSON)",
                                    "https://open.er-api.com/v6/latest/USD",
                                    SourceType.JSON),
                            new DataSource("Webgia - Tỷ giá",
                                    "https://webgia.com/ty-gia/",
                                    SourceType.HTML))),
            // ── Crypto ──
            new TopicSources(
                    Pattern.compile("\\b(bitcoin|ethereum|btc|eth|crypto|tiền điện tử|tiền ảo|giá coin|giá \\w*coin)\\b",
                            Pattern.CASE_INSENSITIVE),
                    List.of(
                            new DataSource("CoinGecko - Giá crypto (JSON API)",
                                    "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,binancecoin"
                                            + "&vs_currencies=usd,vnd&include_24hr_change=true",
                                    SourceType.JSON))));

    /** Regex tìm con số "giống số liệu thật" để trích context window từ HTML. */
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d{1,3}([.,]\\d{3}){1,}|\\d+([.,]\\d+)?\\s*%");

    /**
     * Kiểm tra query có khớp bất kỳ chủ đề nào trong registry không.
     */
    public boolean hasSourceFor(String query) {
        if (!StringUtils.hasText(query))
            return false;
        String lower = query.toLowerCase(Locale.ROOT);
        return REGISTRY.stream().anyMatch(t -> t.pattern().matcher(lower).find());
    }

    /**
     * Fetch tất cả nguồn dữ liệu khớp với {@code query}, trả về danh sách kết
     * quả (mỗi nguồn = một {@link GoogleWebSearchResult}). Nguồn nào lỗi sẽ bị
     * bỏ qua. Trả về list rỗng nếu không khớp chủ đề nào hoặc không lấy được dữ
     * liệu.
     */
    public List<GoogleWebSearchResult> fetchForQuery(String query) {
        List<GoogleWebSearchResult> out = new ArrayList<>();
        if (!StringUtils.hasText(query))
            return out;
        String lower = query.toLowerCase(Locale.ROOT);

        for (TopicSources topic : REGISTRY) {
            if (!topic.pattern().matcher(lower).find())
                continue;
            for (DataSource src : topic.sources()) {
                try {
                    String content = src.type() == SourceType.JSON
                            ? fetchJson(src.url())
                            : fetchHtmlNumbers(src.url());
                    if (StringUtils.hasText(content)) {
                        out.add(GoogleWebSearchResult.builder()
                                .title(src.label())
                                .snippet(content)
                                .link(src.url())
                                .build());
                        log.info("[RealtimeData] OK '{}' ({} ký tự) cho query '{}'",
                                src.label(), content.length(), query);
                    }
                } catch (Exception ex) {
                    log.debug("[RealtimeData] Bỏ qua '{}': {}", src.url(), ex.getMessage());
                }
            }
        }
        return out;
    }

    /** Tải JSON thô và cắt theo giới hạn ký tự. */
    private String fetchJson(String url) throws Exception {
        String body = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .maxBodySize(0)
                .execute()
                .body();
        if (!StringUtils.hasText(body))
            return null;
        String cleaned = body.replaceAll("\\s{2,}", " ").strip();
        return cleaned.length() > maxCharsPerSource
                ? cleaned.substring(0, maxCharsPerSource) + "…"
                : cleaned;
    }

    /**
     * Tải HTML tĩnh, trích các "context window" quanh mỗi con số (để AI thấy
     * nhãn đi kèm như "Vàng SJC", "Mua", "Bán"). Nếu không có số nào → trả null.
     */
    private String fetchHtmlNumbers(String url) throws Exception {
        org.jsoup.nodes.Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .get();
        doc.select("script, style, noscript, svg, iframe").remove();

        // Ưu tiên các bảng dữ liệu (giá thường nằm trong <table>).
        StringBuilder raw = new StringBuilder();
        for (org.jsoup.nodes.Element table : doc.select("table")) {
            String t = table.text();
            if (PRICE_PATTERN.matcher(t).find()) {
                raw.append(t).append(" \n");
            }
        }
        // Fallback: toàn bộ body nếu không có table chứa số.
        String text = raw.length() > 0 ? raw.toString()
                : (doc.body() != null ? doc.body().text() : doc.text());

        // Trích các đoạn context quanh số để giữ nhãn đi kèm.
        Set<String> windows = new LinkedHashSet<>();
        Matcher m = PRICE_PATTERN.matcher(text);
        while (m.find() && windows.size() < 40) {
            int start = Math.max(0, m.start() - 40);
            int end = Math.min(text.length(), m.end() + 8);
            windows.add(text.substring(start, end).replaceAll("\\s{2,}", " ").strip());
        }
        if (windows.isEmpty())
            return null;

        String joined = String.join(" | ", windows);
        return joined.length() > maxCharsPerSource
                ? joined.substring(0, maxCharsPerSource) + "…"
                : joined;
    }
}
