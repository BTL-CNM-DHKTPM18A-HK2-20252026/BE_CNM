package iuh.fit.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    @JsonProperty("access_token")
    String accessToken;

    @JsonProperty("refresh_token")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String refreshToken;

    @JsonProperty("expires_in")
    long expiresIn; // in seconds

    @JsonProperty("token_type")
    String tokenType;
}
