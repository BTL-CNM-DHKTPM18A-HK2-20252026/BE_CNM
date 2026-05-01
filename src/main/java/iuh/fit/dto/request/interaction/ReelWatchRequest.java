package iuh.fit.dto.request.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReelWatchRequest {
    private String reelId;
    private Double watchedDuration;
    private Double totalDuration;
    private Boolean isCompleted;
    private Integer rewatchCount;
    private String source;
}
