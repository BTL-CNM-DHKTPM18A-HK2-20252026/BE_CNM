package iuh.fit.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCoverPhotoRequest {
    @JsonProperty("cover_photo_url")
    private String coverPhotoUrl;
}
