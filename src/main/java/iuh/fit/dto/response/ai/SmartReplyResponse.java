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

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public static SmartReplyResponseBuilder builder() {
        return new SmartReplyResponseBuilder();
    }

    public static class SmartReplyResponseBuilder {
        private final SmartReplyResponse response = new SmartReplyResponse();

        public SmartReplyResponseBuilder suggestions(List<String> suggestions) { response.setSuggestions(suggestions); return this; }
        public SmartReplyResponseBuilder model(String model) { response.setModel(model); return this; }
        public SmartReplyResponseBuilder latencyMs(long latencyMs) { response.setLatencyMs(latencyMs); return this; }
        public SmartReplyResponse build() { return response; }
    }
}
