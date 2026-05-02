package iuh.fit.service.ai.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRouterResult {

    public static final String TASK_CORE_CHAT = "CORE_CHAT";
    public static final String TASK_REASONING_CODE = "REASONING_CODE";
    public static final String TASK_KNOWLEDGE = "KNOWLEDGE";
    public static final String TASK_IMAGE_GEN = "IMAGE_GEN";
    public static final String TASK_VISION = "VISION";

    private String selectedModel;
    private String taskType;
    private String reason;
    private String refinedPrompt;
}
