package iuh.fit.dto.request.story;

import iuh.fit.enums.PrivacyLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryRequest {

    @NotBlank
    private String mediaUrl;

    @NotBlank
    private String mediaType; // IMAGE, VIDEO, TEXT

    private String caption;

    private Integer duration; // nullable for IMAGE/TEXT

    private PrivacyLevel privacy; // PUBLIC / FRIEND_ONLY / ADMIN
}