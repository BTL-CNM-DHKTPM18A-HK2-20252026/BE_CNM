package iuh.fit.dto.response.post;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMediaResponse {
    private String mediaId;
    private String url;
    private String type;
    private String altText;
}
