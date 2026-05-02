package iuh.fit.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO đại diện cho một kết quả tìm kiếm hình ảnh từ Google Custom Search API.
 *
 * <ul>
 * <li>{@code title} — tiêu đề mô tả hình ảnh</li>
 * <li>{@code imageUrl} — URL trực tiếp đến file ảnh (JPEG/PNG/…)</li>
 * <li>{@code contextLink} — URL trang web chứa hình ảnh đó</li>
 * <li>{@code thumbnailUrl} — URL ảnh thumbnail nhỏ do Google lưu cache</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleImageSearchResult {

    /** Tiêu đề mô tả ảnh được Google trả về. */
    private String title;

    /**
     * URL trực tiếp đến file hình ảnh (trường {@code link} khi searchType=image).
     */
    private String imageUrl;

    /** URL trang web nguồn chứa hình ảnh ({@code image.contextLink}). */
    private String contextLink;

    /** URL ảnh thumbnail nhỏ do Google lưu cache ({@code image.thumbnailLink}). */
    private String thumbnailUrl;
}
