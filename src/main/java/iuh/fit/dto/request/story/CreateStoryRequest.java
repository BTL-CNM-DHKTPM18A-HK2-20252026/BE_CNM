package iuh.fit.dto.request.story;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryRequest {
    
    private String mediaUrl;
    
    private String caption;
    
    private String background;
    
    @NotBlank(message = "Media type is required")
    private String mediaType; // IMAGE, VIDEO, or TEXT
}
