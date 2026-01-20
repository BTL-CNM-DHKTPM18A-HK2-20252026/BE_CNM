package iuh.fit.dto.request.story;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryRequest {
    
    @NotBlank(message = "Media URL is required")
    private String mediaUrl;
    
    private String caption;
    
    @NotBlank(message = "Media type is required")
    private String mediaType; // IMAGE or VIDEO
}
