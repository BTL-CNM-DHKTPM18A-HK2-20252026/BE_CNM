package iuh.fit.dto.response.user;

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
public class UserMeResponse {

    String id;

    @JsonProperty("full_name")
    String fullName;

    @JsonProperty("gender")
    String gender;

    @JsonProperty("dob")
    Date dob;

    String email;

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
