package iuh.fit.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO đại diện cho một kết quả tìm kiếm văn bản từ Google Custom Search API.
 *
 * <ul>
 * <li>{@code title} — tiêu đề trang web</li>
 * <li>{@code snippet} — đoạn mô tả ngắn do Google trích xuất</li>
 * <li>{@code link} — URL trang web nguồn</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleWebSearchResult {

    /** Tiêu đề kết quả tìm kiếm. */
    private String title;

    /** Đoạn mô tả ngắn (snippet) được Google trích xuất từ trang. */
    private String snippet;

    /** URL đầy đủ của trang web nguồn. */
    private String link;
}
