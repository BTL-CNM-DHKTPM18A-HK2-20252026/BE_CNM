package iuh.fit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequest {
    
    @NotBlank(message = "Post content is required")
    @Size(max = 10000, message = "Post content must not exceed 10000 characters")
    private String content;
    
    private String location;
}
