package iuh.fit.service.ai.features.search;

import iuh.fit.dto.response.ai.GoogleWebSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Phát hiện truy vấn cần dữ liệu thời gian thực (tin tức, tỷ số, giá cả, …)
 * và bổ sung kết quả Google Search vào context trước khi gọi AI.
 *
 * <p>
 * Được inject vào {@code AiChatService} để thay thế kiến thức cũ (training
 * data) bằng thông tin mới nhất từ internet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSearchContextService {

    private final GoogleSearchService googleSearchService;
    private final DuckDuckGoSearchService duckDuckGoSearchService;
    private final RealtimeDataSourceRegistry realtimeDataSourceRegistry;

    /** Số kết quả tối đa đưa vào context (tránh vượt token budget). */
    private static final int MAX_INJECT_RESULTS = 5;

    private static final String DEEP_RESEARCH_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /** Deep research: bật/tắt việc tải toàn bộ nội dung trang. */
    @Value("${ai.deep-research.enabled:true}")
    private boolean deepResearchEnabled;

    /** Số trang đầu được tải full nội dung (chi phí latency cao, giữ nhỏ). */
    @Value("${ai.deep-research.max-pages:3}")
    private int deepResearchMaxPages;

    /** Giới hạn ký tự trích từ mỗi trang để không vượt token budget. */
    @Value("${ai.deep-research.max-chars-per-page:2000}")
    private int deepResearchMaxCharsPerPage;

    /** Timeout (ms) cho mỗi lần fetch full nội dung. */
    @Value("${ai.deep-research.timeout-ms:8000}")
    private int deepResearchTimeoutMs;

    /**
     * Các pattern từ khoá chỉ ra rằng người dùng cần dữ liệu thực tế / mới nhất.
     * Kiểm tra tiếng Việt lẫn tiếng Anh.
     */
    private static final List<Pattern> REALTIME_PATTERNS = List.of(
            // Thời gian hiện tại
            Pattern.compile("\\b(hôm nay|ngày hôm nay|hiện tại|hiện nay|bây giờ|lúc này|ngay bây giờ)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Gần đây / mới nhất
            Pattern.compile("\\b(mới nhất|gần nhất|vừa qua|vừa rồi|mới đây|gần đây|cập nhật|latest|recent)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Thời gian tương đối
            Pattern.compile("\\b(tuần này|tuần trước|tháng này|tháng trước|năm nay|this week|this month|this year)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Tin tức / thời sự
            Pattern.compile("\\b(tin tức|tin mới|thời sự|sự kiện|breaking news|news)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Thể thao
            Pattern.compile(
                    "\\b(kết quả|tỷ số|điểm số|vòng \\d+|bảng xếp hạng|lịch thi đấu|standings|fixtures|score|match result)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Tài chính / giá cả
            Pattern.compile(
                    "\\b(giá vàng|giá dầu|giá xăng|tỷ giá|giá cổ phiếu|chứng khoán|vn-index|dow jones|nasdaq)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Crypto
            Pattern.compile("\\b(bitcoin|ethereum|btc|eth|crypto|tiền ảo|tiền điện tử|coin)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Chỉ thị hiện tại chung
            Pattern.compile("\\b(today|right now|currently|current price|right now|as of today)\\b",
                    Pattern.CASE_INSENSITIVE));

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Kiểm tra xem {@code query} có cần tra cứu dữ liệu thực tế không.
     *
     * @param query nội dung người dùng nhập
     * @return {@code true} nếu phát hiện từ khoá thời gian thực
     */
    public boolean isRealTimeQuery(String query) {
        if (!StringUtils.hasText(query))
            return false;
        String lower = query.toLowerCase(Locale.ROOT);
        return REALTIME_PATTERNS.stream().anyMatch(p -> p.matcher(lower).find());
    }

    /**
     * Gọi Google Search, lấy tối đa {@value MAX_INJECT_RESULTS} kết quả hàng đầu,
     * định dạng thành khối system-prompt và trả về.
     *
     * <p>
     * Trả về {@code null} nếu tìm kiếm thất bại hoặc không có kết quả —
     * caller nên bỏ qua thay vì ném lỗi.
     *
     * @param query    từ khoá tìm kiếm (thường là nội dung người dùng)
     * @param language "vi" hoặc "en"
     * @return nội dung system prompt chứa kết quả tìm kiếm, hoặc {@code null}
     */
    public String buildSearchContext(String query, String language) {
        try {
            // ── Bước 0: nguồn dữ liệu số chuyên dụng (giá vàng, tỷ giá, crypto…) ──
            // Lấy số THẬT từ JSON API / HTML tĩnh trước, ưu tiên cao nhất.
            List<GoogleWebSearchResult> realtimeData = List.of();
            if (isNumericDataQuery(query)) {
                try {
                    realtimeData = realtimeDataSourceRegistry.fetchForQuery(query);
                } catch (Exception dataEx) {
                    log.debug("[GoogleSearchContext] Registry dữ liệu số lỗi: {}", dataEx.getMessage());
                }
            }

            // ── Bước 1: thử Google Custom Search (nguồn chính) ──
            String source = "GOOGLE";
            List<GoogleWebSearchResult> results;
            try {
                results = googleSearchService.searchInformation(query);
            } catch (Exception googleEx) {
                // Google lỗi (403 quota/billing, timeout, …) → chuyển dự phòng.
                log.warn("[GoogleSearchContext] Google lỗi ({}), chuyển DuckDuckGo dự phòng.",
                        googleEx.getMessage());
                results = List.of();
            }

            // ── Bước 2: fallback DuckDuckGo nếu Google rỗng/lỗi ──
            if (results.isEmpty()) {
                results = duckDuckGoSearchService.searchInformation(query);
                source = "DUCKDUCKGO";
            }

            if (results.isEmpty() && realtimeData.isEmpty()) {
                log.info("[GoogleSearchContext] Không có kết quả tìm kiếm (cả Google & DuckDuckGo) cho: '{}'", query);
                return null;
            }

            // Ghép: nguồn dữ liệu số (ưu tiên) đứng trước kết quả web thường.
            List<GoogleWebSearchResult> merged = new java.util.ArrayList<>(realtimeData);
            merged.addAll(results);

            List<GoogleWebSearchResult> top = merged.stream().limit(MAX_INJECT_RESULTS).toList();
            boolean isVi = "vi".equals(language);
            String today = LocalDate.now().toString();
            String sourceLabel = !realtimeData.isEmpty()
                    ? "NGUỒN DỮ LIỆU CHUYÊN DỤNG + WEB"
                    : ("GOOGLE".equals(source) ? "GOOGLE SEARCH" : "DUCKDUCKGO SEARCH");

            StringBuilder sb = new StringBuilder();

            // ── Header cứng — AI KHÔNG được bỏ qua ──
            sb.append("════════════════════════════════════════\n");
            if (isVi) {
                sb.append("⚠️  LỆNH HỆ THỐNG — ƯU TIÊN TUYỆT ĐỐI — NGÀY: ").append(today).append("\n");
                sb.append("════════════════════════════════════════\n\n");
                sb.append("BẠN VỪA ĐƯỢC CẤP KẾT QUẢ TÌM KIẾM WEB MỚI NHẤT.\n");
                sb.append("NGHIÊM CẤM dùng bất kỳ con số / sự kiện nào từ training data.\n");
                sb.append("CHỈ được phép dùng dữ liệu từ các nguồn bên dưới.\n\n");
                sb.append("── KẾT QUẢ ").append(sourceLabel).append(" (").append(top.size()).append(" nguồn) ──\n\n");
            } else {
                sb.append("⚠️  SYSTEM COMMAND — ABSOLUTE PRIORITY — DATE: ").append(today).append("\n");
                sb.append("════════════════════════════════════════\n\n");
                sb.append("YOU HAVE JUST RECEIVED THE LATEST WEB SEARCH RESULTS.\n");
                sb.append("STRICTLY FORBIDDEN to use any number / event from training data.\n");
                sb.append("ONLY allowed to use data from the sources below.\n\n");
                sb.append("── ").append(sourceLabel).append(" RESULTS (").append(top.size()).append(" sources) ──\n\n");
            }

            // ── Liệt kê kết quả — tiêu đề + snippet + link ──
            // Đồng thời gom toàn bộ text (snippet + nội dung chi tiết) để guard
            // kiểm tra xem có con số thật hay không.
            StringBuilder evidence = new StringBuilder();
            int idx = 1;
            for (GoogleWebSearchResult r : top) {
                String title = StringUtils.hasText(r.getTitle()) ? r.getTitle() : "(no title)";
                String snippet = StringUtils.hasText(r.getSnippet()) ? r.getSnippet() : "";
                String link = StringUtils.hasText(r.getLink()) ? r.getLink() : "";

                sb.append("[").append(idx).append("] ").append(title).append("\n");
                if (!snippet.isBlank()) {
                    sb.append("    📄 ").append(snippet).append("\n");
                    evidence.append(' ').append(snippet);
                }
                if (!link.isBlank()) {
                    // Lưu link gốc để AI có thể render thành markdown
                    sb.append("    🔗 URL: ").append(link).append("\n");
                }

                // ── Deep research: tải toàn bộ nội dung trang cho top N nguồn ──
                if (deepResearchEnabled && idx <= deepResearchMaxPages && !link.isBlank()) {
                    String fullText = fetchFullContent(link);
                    if (StringUtils.hasText(fullText)) {
                        sb.append(isVi ? "    📰 NỘI DUNG CHI TIẾT:\n" : "    📰 FULL CONTENT:\n");
                        sb.append("    ").append(fullText.replace("\n", "\n    ")).append("\n");
                        evidence.append(' ').append(fullText);
                    }
                }

                sb.append("\n");
                idx++;
            }

            // ── GUARD CHỐNG BỊA SỐ ──
            // Nếu câu hỏi đòi số liệu (giá vàng/dầu/tỷ giá/chứng khoán/crypto…)
            // nhưng kết quả tìm kiếm KHÔNG chứa con số thật → KHÔNG đưa kết quả
            // mơ hồ cho AI (AI sẽ bịa). Thay bằng lệnh từ chối dứt khoát.
            if (isNumericDataQuery(query) && !containsRealNumbers(evidence.toString())) {
                log.info("[GoogleSearchContext] Guard kích hoạt: câu hỏi số liệu '{}' nhưng "
                        + "không có con số thật trong kết quả → trả lệnh từ chối.", query);
                return buildNoDataGuard(query, language, today, top);
            }

            // ── Hướng dẫn bắt buộc về cách trả lời ──
            if (isVi) {
                sb.append("════════════════════════════════════════\n");
                sb.append("QUY TẮC TRẢ LỜI BẮT BUỘC:\n");
                sb.append("1. Lấy số liệu / sự kiện TRỰC TIẾP từ phần 📄 ở trên.\n");
                sb.append("2. KHÔNG được tự bịa hoặc dùng số từ bộ nhớ cũ.\n");
                sb.append("3. Nếu snippet KHÔNG có số liệu cụ thể, hãy nói thẳng:\n");
                sb.append("   \"Tôi không tìm thấy con số chính xác trong kết quả tìm kiếm này.\"\n");
                sb.append("4. Cuối câu trả lời, PHẢI liệt kê nguồn dưới dạng markdown link:\n");
                sb.append("   **Nguồn tham khảo:**\n");
                sb.append("   - [Tên nguồn 1](URL_1)\n");
                sb.append("   - [Tên nguồn 2](URL_2)\n");
                sb.append("   (thay Tên nguồn và URL bằng title + URL thực từ kết quả trên)\n");
                sb.append("════════════════════════════════════════\n");
            } else {
                sb.append("════════════════════════════════════════\n");
                sb.append("MANDATORY RESPONSE RULES:\n");
                sb.append("1. Extract numbers / events DIRECTLY from the 📄 snippets above.\n");
                sb.append("2. NEVER fabricate or use numbers from your own memory.\n");
                sb.append("3. If the snippet has no specific number, say explicitly:\n");
                sb.append("   \"I could not find an exact figure in these search results.\"\n");
                sb.append("4. End your answer with sources as markdown links:\n");
                sb.append("   **Sources:**\n");
                sb.append("   - [Source Name 1](URL_1)\n");
                sb.append("   - [Source Name 2](URL_2)\n");
                sb.append("   (replace with actual title + URL from the results above)\n");
                sb.append("════════════════════════════════════════\n");
            }

            log.info("[GoogleSearchContext] Đã build context {} kết quả cho: '{}'", top.size(), query);
            return sb.toString();

        } catch (Exception ex) {
            log.warn("[GoogleSearchContext] Tìm kiếm thất bại cho '{}': {}", query, ex.getMessage());
            return null;
        }
    }

    // ── Guard chống bịa số ─────────────────────────────────────────────────────

    /** Pattern câu hỏi đòi CON SỐ cụ thể (giá/tỷ giá/chỉ số…). */
    private static final Pattern NUMERIC_QUERY_PATTERN = Pattern.compile(
            "\\b(giá vàng|giá dầu|giá xăng|giá bạc|tỷ giá|giá cổ phiếu|chứng khoán|vn-?index|"
                    + "dow jones|nasdaq|bitcoin|ethereum|btc|eth|giá coin|giá \\w+|"
                    + "exchange rate|stock price|gold price|oil price|price of)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Phát hiện chuỗi text bằng chứng (snippet + nội dung trang) có chứa
     * con số "giống số liệu thật" hay không. Yêu cầu: có ít nhất một số có
     * từ 3 chữ số trở lên, hoặc số kèm đơn vị tiền tệ/giá phổ biến.
     */
    private static final Pattern REAL_NUMBER_PATTERN = Pattern.compile(
            "\\d[\\d.,]{2,}\\s*(triệu|tỷ|nghìn|đồng|vnđ|vnd|usd|đô|lượng|chỉ|ounce|oz|%)"
                    + "|(triệu|tỷ|nghìn|đồng|vnđ|vnd|usd|\\$|₫)\\s*\\d[\\d.,]{2,}"
                    + "|\\d{1,3}([.,]\\d{3}){1,}",
            Pattern.CASE_INSENSITIVE);

    private boolean isNumericDataQuery(String query) {
        return StringUtils.hasText(query)
                && NUMERIC_QUERY_PATTERN.matcher(query.toLowerCase(Locale.ROOT)).find();
    }

    private boolean containsRealNumbers(String evidence) {
        return StringUtils.hasText(evidence)
                && REAL_NUMBER_PATTERN.matcher(evidence).find();
    }

    /**
     * Khi câu hỏi đòi số liệu nhưng tìm kiếm không có con số thật, trả về một
     * khối system-prompt buộc AI từ chối bịa và hướng người dùng tới nguồn
     * chính thống (kèm các link đã tìm được).
     */
    private String buildNoDataGuard(String query, String language, String today,
            List<GoogleWebSearchResult> sources) {
        boolean isVi = "vi".equals(language);
        StringBuilder sb = new StringBuilder();
        sb.append("════════════════════════════════════════\n");
        if (isVi) {
            sb.append("⚠️  LỆNH HỆ THỐNG — ƯU TIÊN TUYỆT ĐỐI — NGÀY: ").append(today).append("\n");
            sb.append("════════════════════════════════════════\n\n");
            sb.append("Hệ thống ĐÃ tìm kiếm web nhưng KHÔNG lấy được con số chính xác cho câu hỏi này.\n");
            sb.append("NGHIÊM CẤM TUYỆT ĐỐI việc tự bịa ra bất kỳ con số nào (giá, tỷ giá, chỉ số…).\n\n");
            sb.append("BẮT BUỘC trả lời theo đúng tinh thần sau (diễn đạt tự nhiên bằng giọng của bạn):\n");
            sb.append("  • Nói thẳng rằng bạn KHÔNG có số liệu thời gian thực chính xác ở thời điểm này.\n");
            sb.append("  • TUYỆT ĐỐI KHÔNG đưa ra con số ước lượng / từ trí nhớ.\n");
            sb.append("  • Hướng người dùng kiểm tra tại nguồn chính thống mới nhất.\n");
            if (sources != null && !sources.isEmpty()) {
                sb.append("\nGợi ý nguồn để liệt kê cuối câu trả lời (markdown link):\n");
                for (GoogleWebSearchResult r : sources) {
                    if (StringUtils.hasText(r.getLink())) {
                        String t = StringUtils.hasText(r.getTitle()) ? r.getTitle() : r.getLink();
                        sb.append("  - [").append(t).append("](").append(r.getLink()).append(")\n");
                    }
                }
            }
        } else {
            sb.append("⚠️  SYSTEM COMMAND — ABSOLUTE PRIORITY — DATE: ").append(today).append("\n");
            sb.append("════════════════════════════════════════\n\n");
            sb.append("The system searched the web but could NOT retrieve an exact figure for this question.\n");
            sb.append("STRICTLY FORBIDDEN to fabricate any number (price, rate, index…).\n\n");
            sb.append("You MUST answer in this spirit (phrase naturally in your own voice):\n");
            sb.append("  • State clearly that you do NOT have an exact real-time figure right now.\n");
            sb.append("  • NEVER provide an estimated number / from memory.\n");
            sb.append("  • Direct the user to check an authoritative, up-to-date source.\n");
            if (sources != null && !sources.isEmpty()) {
                sb.append("\nSuggested sources to list at the end (markdown links):\n");
                for (GoogleWebSearchResult r : sources) {
                    if (StringUtils.hasText(r.getLink())) {
                        String t = StringUtils.hasText(r.getTitle()) ? r.getTitle() : r.getLink();
                        sb.append("  - [").append(t).append("](").append(r.getLink()).append(")\n");
                    }
                }
            }
        }
        sb.append("════════════════════════════════════════\n");
        return sb.toString();
    }

    /**
     * Deep research — tải toàn bộ nội dung một trang web trực tiếp bằng
     * <b>Jsoup</b>, lọc lấy phần thân bài (bỏ script/style/nav) và trả về văn
     * bản thuần đã được làm sạch.
     *
     * <p>
     * Dùng Jsoup thay cho Jina Reader vì Jina Reader hiện yêu cầu API key /
     * bị chặn (HTTP 451). Mọi lỗi (timeout, 4xx/5xx, network) đều được nuốt và
     * trả về {@code null} — deep research là tính năng bổ trợ, không được làm
     * hỏng luồng chat chính.
     *
     * @param url URL gốc của kết quả tìm kiếm
     * @return nội dung trang đã rút gọn (≤ {@code deepResearchMaxCharsPerPage} ký
     *         tự), hoặc {@code null} nếu không lấy được
     */
    private String fetchFullContent(String url) {
        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent(DEEP_RESEARCH_USER_AGENT)
                    .timeout(deepResearchTimeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .get();

            // Loại bỏ các phần nhiễu trước khi trích văn bản.
            doc.select("script, style, nav, header, footer, noscript, iframe, svg").remove();

            // Ưu tiên phần thân bài; fallback về toàn bộ body.
            org.jsoup.nodes.Element main = doc.selectFirst("article, main, div[class*=content], div[class*=article]");
            String text = (main != null ? main.text() : doc.body() != null ? doc.body().text() : doc.text());

            if (!StringUtils.hasText(text)) {
                return null;
            }

            // Chuẩn hoá khoảng trắng và cắt theo giới hạn ký tự.
            String cleaned = text.replaceAll("\\s{2,}", " ").strip();
            if (cleaned.length() > deepResearchMaxCharsPerPage) {
                cleaned = cleaned.substring(0, deepResearchMaxCharsPerPage) + "…";
            }
            log.info("[GoogleSearchContext] Deep research OK ({} ký tự) cho: {}", cleaned.length(), url);
            return cleaned;

        } catch (Exception ex) {
            log.debug("[GoogleSearchContext] Deep research bỏ qua '{}': {}", url, ex.getMessage());
            return null;
        }
    }
}
