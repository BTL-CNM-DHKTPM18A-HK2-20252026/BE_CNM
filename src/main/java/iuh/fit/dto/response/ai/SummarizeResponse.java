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

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int count) { this.messageCount = count; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public static SummarizeResponseBuilder builder() {
        return new SummarizeResponseBuilder();
    }

    public static class SummarizeResponseBuilder {
        private final SummarizeResponse response = new SummarizeResponse();

        public SummarizeResponseBuilder summary(String summary) { response.setSummary(summary); return this; }
        public SummarizeResponseBuilder messageCount(int count) { response.setMessageCount(count); return this; }
        public SummarizeResponseBuilder model(String model) { response.setModel(model); return this; }
        public SummarizeResponseBuilder latencyMs(long latencyMs) { response.setLatencyMs(latencyMs); return this; }
        public SummarizeResponse build() { return response; }
    }
}
