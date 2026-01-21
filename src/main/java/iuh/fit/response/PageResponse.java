package iuh.fit.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper cho paginated data trong Fruvia Chat.
 * Bao gồm data, page info và total count.
 * 
 * @param <T> Kiểu dữ liệu của items trong page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {
    
    /**
     * Danh sách items trong page hiện tại
     */
    private List<T> items;
    
    /**
     * Thông tin về pagination
     */
    private PageInfo pageInfo;
    
    /**
     * Tổng số items (across all pages)
     */
    private long totalItems;
    
    /**
     * Tổng số pages
     */
    private int totalPages;

    // ==================== STATIC FACTORY METHOD ====================
    
    /**
     * Tạo PageResponse từ Spring Data Page object
     */
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
            .items(page.getContent())
            .pageInfo(PageInfo.builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build())
            .totalItems(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .build();
    }
    
    /**
     * Tạo PageResponse từ list và page info
     */
    public static <T> PageResponse<T> of(List<T> items, int currentPage, int pageSize, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        
        return PageResponse.<T>builder()
            .items(items)
            .pageInfo(PageInfo.builder()
                .currentPage(currentPage)
                .pageSize(pageSize)
                .hasNext(currentPage < totalPages - 1)
                .hasPrevious(currentPage > 0)
                .isFirst(currentPage == 0)
                .isLast(currentPage == totalPages - 1)
                .build())
            .totalItems(totalItems)
            .totalPages(totalPages)
            .build();
    }

    // ==================== INNER CLASS ====================
    
    /**
     * Thông tin chi tiết về pagination
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        /**
         * Page hiện tại (0-indexed)
         */
        private int currentPage;
        
        /**
         * Số items mỗi page
         */
        private int pageSize;
        
        /**
         * Có page tiếp theo không
         */
        private boolean hasNext;
        
        /**
         * Có page trước không
         */
        private boolean hasPrevious;
        
        /**
         * Có phải page đầu tiên không
         */
        private boolean isFirst;
        
        /**
         * Có phải page cuối cùng không
         */
        private boolean isLast;
    }
}
