package iuh.fit.dto.response.image;

import java.time.LocalDateTime;

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
public class ImageUploadMetadataResponse {

    String id;

    String originalName;

    String s3Key;

    String s3Url;

    int width;

    int height;

    LocalDateTime uploadTime;
}
