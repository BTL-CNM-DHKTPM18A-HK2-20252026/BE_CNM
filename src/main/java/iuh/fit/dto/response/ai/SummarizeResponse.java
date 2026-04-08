package iuh.fit.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummarizeResponse {
    private String summary;
    private int messageCount;
    private String model;
    private long latencyMs;
}
