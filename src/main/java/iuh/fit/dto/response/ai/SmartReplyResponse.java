package iuh.fit.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartReplyResponse {
    private List<String> suggestions;
    private String model;
    private long latencyMs;
}
