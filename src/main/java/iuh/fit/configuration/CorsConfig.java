package iuh.fit.configuration;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CorsConfig {

    private static final String DEFAULT_ALLOWED_ORIGINS =
            "http://fruvia-web-074095961202.s3-website-ap-southeast-1.amazonaws.com,http://localhost:3000";
    private static final String DEFAULT_ALLOWED_METHODS = "GET,POST,PUT,DELETE,PATCH,OPTIONS";
    private static final String DEFAULT_ALLOWED_HEADERS = "*";
    private static final String DEFAULT_EXPOSED_HEADERS = "Authorization";

    // Port configuration:
    // 3000: React/Next.js development server
    // 5173: Vite development server (Vue/React)
    // 8081: Mobile app (React Native/Flutter) or alternative frontend
    @Value("${cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:" + DEFAULT_ALLOWED_METHODS + "}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:" + DEFAULT_ALLOWED_HEADERS + "}")
    private String allowedHeaders;

    @Value("${cors.exposed-headers:" + DEFAULT_EXPOSED_HEADERS + "}")
    private String exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> resolvedOrigins = parseCsvOrDefault(allowedOrigins, DEFAULT_ALLOWED_ORIGINS);
        List<String> resolvedMethods = parseCsvOrDefault(allowedMethods, DEFAULT_ALLOWED_METHODS);
        List<String> resolvedHeaders = parseCsvOrDefault(allowedHeaders, DEFAULT_ALLOWED_HEADERS);
        List<String> resolvedExposedHeaders = parseCsvOrDefault(exposedHeaders, DEFAULT_EXPOSED_HEADERS);

        // Allowed origins (Web + Mobile)
        configuration.setAllowedOrigins(resolvedOrigins);

        // Allowed HTTP methods
        configuration.setAllowedMethods(resolvedMethods);

        // Allowed headers
        configuration.setAllowedHeaders(resolvedHeaders);

        // Exposed headers (important for Authorization tokens)
        configuration.setExposedHeaders(resolvedExposedHeaders);

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(allowCredentials);

        // Cache preflight requests
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("Configured CORS allowed origins: {}", resolvedOrigins);

        return source;
    }

    private List<String> parseCsvOrDefault(String value, String defaultValue) {
        String effectiveValue = (value == null || value.isBlank()) ? defaultValue : value;
        return Arrays.stream(effectiveValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
