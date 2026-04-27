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

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public static AuthenticationResponseBuilder builder() {
        return new AuthenticationResponseBuilder();
    }

    public static class AuthenticationResponseBuilder {
        private final AuthenticationResponse response = new AuthenticationResponse();

        public AuthenticationResponseBuilder accessToken(String token) { response.setAccessToken(token); return this; }
        public AuthenticationResponseBuilder refreshToken(String token) { response.setRefreshToken(token); return this; }
        public AuthenticationResponseBuilder expiresIn(long expires) { response.setExpiresIn(expires); return this; }
        public AuthenticationResponseBuilder tokenType(String type) { response.setTokenType(type); return this; }
        public AuthenticationResponse build() { return response; }
    }
}
