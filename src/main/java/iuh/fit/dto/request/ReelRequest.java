package iuh.fit.dto.request;

import iuh.fit.enums.PrivacyLevel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReelRequest {
    String videoUrl;
    String thumbnailUrl;
    String caption;
    String musicId;
    String musicTitle;
    String musicArtist;
    PrivacyLevel privacy;
    Boolean allowComments;
    Boolean allowSharing;
}
