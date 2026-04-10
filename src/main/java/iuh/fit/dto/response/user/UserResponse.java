package iuh.fit.dto.response.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

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
public class UserResponse {

    @JsonProperty("user_id")
    String userId;

    @JsonProperty("phone_number")
    String phoneNumber;

    String gmail;

    @JsonProperty("display_name")
    String displayName;

    @JsonProperty("first_name")
    String firstName;

    @JsonProperty("last_name")
    String lastName;

    @JsonProperty("avatar_url")
    String avatarUrl;

    @JsonProperty("account_status")
    String accountStatus;

    @JsonProperty("is_verified")
    Boolean isVerified;

    String gender;

    @JsonProperty("dob")
    Date dob;

    @JsonProperty("friendship_status")
    String friendshipStatus;

    @JsonProperty("is_requester")
    Boolean isRequester;

    @JsonProperty("cover_photo_url")
    String coverPhotoUrl;

    String bio;
}
