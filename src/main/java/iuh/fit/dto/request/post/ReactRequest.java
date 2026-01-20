package iuh.fit.dto.request.post;

import iuh.fit.enums.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactRequest {
    
    @NotNull(message = "Reaction type is required")
    private ReactionType reactionType;
}
