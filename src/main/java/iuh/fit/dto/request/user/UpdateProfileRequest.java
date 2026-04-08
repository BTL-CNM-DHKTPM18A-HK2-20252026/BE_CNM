package iuh.fit.dto.request.user;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public class UpdateProfileRequest {
    
    @JsonProperty("full_name")
    String fullName;
    
    String gender;
    
    Date dob;

    String bio;
    String address;
    String city;
    String education;
    String workplace;
    
    @JsonProperty("avatar_url")
    String avatarUrl;
    
    @JsonProperty("cover_photo_url")
    String coverPhotoUrl;
}
