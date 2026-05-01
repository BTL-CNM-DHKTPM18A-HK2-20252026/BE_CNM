package iuh.fit.dto.request.post;

import java.util.List;

import iuh.fit.enums.PrivacyLevel;
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

    private PrivacyLevel privacy;
    
    private String location;
    
    private List<PostMediaRequest> media; // List of media with optional alt text
    
    private List<String> taggedUserIds; // List of tagged users

    private Boolean hideLikes;
    private Boolean turnOffComments;

    private String linkUrl; // URL for link posts

    private String sharedPostId; // ID of the original post being shared
}
