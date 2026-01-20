package iuh.fit.dto.request.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentPostRequest {
    
    @NotBlank(message = "Comment content is required")
    @Size(max = 2000, message = "Comment content must not exceed 2000 characters")
    private String content;
    
    private String parentCommentId; // For nested comments/replies
}
