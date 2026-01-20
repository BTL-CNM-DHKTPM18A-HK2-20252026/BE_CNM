package iuh.fit.dto.request.post;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {
    
    @NotBlank(message = "Post content is required")
    @Size(max = 10000, message = "Post content must not exceed 10000 characters")
    private String content;
    
    private String location;
    
    private List<String> mediaUrls; // List of image/video URLs
    
    private List<String> taggedUserIds; // List of tagged users
}
