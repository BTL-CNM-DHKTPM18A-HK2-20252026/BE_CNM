package iuh.fit.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS Configuration for Web App and Mobile App
 * Allows cross-origin requests from frontend applications
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8081}")
    private String[] allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String[] allowedMethods;

    @Value("${cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${cors.exposed-headers:Authorization,Content-Type,X-Total-Count}")
    private String[] exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins (Web + Mobile)
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));

        // For mobile apps that don't send origin header
        configuration.setAllowedOriginPatterns(List.of("*"));

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));

        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));

        // Exposed headers (important for Authorization tokens)
        configuration.setExposedHeaders(Arrays.asList(exposedHeaders));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(allowCredentials);

        // Cache preflight requests
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
