package iuh.fit.dto.response.user;

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
public class UserResponse {
    
    @JsonProperty("user_id")
    String userId;
    
    @JsonProperty("phone_number")
    String phoneNumber;
    
    String email;
    
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

    String gender;
    
    @JsonProperty("dob")
    java.util.Date dob;
}

