package iuh.fit.dto.request.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatWithAiRequest {

    // Optional: if not provided, backend creates or reuses AI conversation
    private String conversationId;

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content must not exceed 5000 characters")
    private String content;

    // Optional control flags
    private Boolean useRag;
    private Integer ragTopK;

    // Optional UI locale hint: vi | en
    private String language;

    // Optional theme selector:
    // GENERAL | SALES | OFFICE | GLOBAL | CREATIVE | STUDY | DEV | CODE_REVIEW
    private String themeType;

    // Optional permission flag from client settings.
    // If true, backend injects full user context (profile, history, file metadata).
    private Boolean fullAccessGranted;

    // Optional: S3 image URL when user sends an image in AI chat for vision
    // analysis
    private String userImageUrl;

    // Optional: S3 document URL (PDF/DOCX) when user sends a document in AI chat
    private String userDocumentUrl;

    // Optional: original file name of the document (used for MIME detection)
    private String userDocumentName;
}
