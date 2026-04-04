package iuh.fit.dto.request.image;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageUploadCompleteRequest {

    @NotBlank(message = "originalName is required")
    String originalName;

    @NotBlank(message = "s3Key is required")
    String s3Key;

    String s3Url;
}
