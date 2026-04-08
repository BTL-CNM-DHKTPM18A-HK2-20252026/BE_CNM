package iuh.fit.configuration;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.access-token.secret}")
    private String accessTokenSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // === PUBLIC ENDPOINTS ===
                        .requestMatchers("/auth/**").permitAll() // Login, logout, refresh, test-cicd
                        .requestMatchers(HttpMethod.POST, "/users").permitAll() // Register account
                        // .requestMatchers(HttpMethod.GET, "/users/**").permitAll()// View user info
                        // (temporary for testing) - Now protected

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight

                        // === Swagger/OpenAPI endpoints ===
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**")
                        .permitAll()

                        // === File endpoints (Temporarily public for testing) ===
                        .requestMatchers("/files/**").permitAll() // Upload & view files (TODO: Add auth for
                                                                  // POST/DELETE)

                        // === WebSocket endpoints ===
                        .requestMatchers("/ws", "/ws/**", "/ws-native", "/ws-native/**").permitAll()

                        // === Utility endpoints ===
                        .requestMatchers("/utils/**").permitAll()

                        // === Actuator: only /health is public, rest requires ADMIN ===
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // === ALL OTHER ENDPOINTS REQUIRE AUTHENTICATION ===
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    // ===== JwtAuthenticationConverter =====
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope"); // Use "scope" as the claim name for roles

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

    // ===== JWT Decoder =====
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(accessTokenSecret.getBytes(), "HmacSHA512");
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> tokenTypeValidator = new JwtClaimValidator<>("type", "access"::equals);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, tokenTypeValidator));

        return jwtDecoder;
    }

    // ===== Password Encoder =====
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
