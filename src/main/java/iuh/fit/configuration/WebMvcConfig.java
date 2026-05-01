package iuh.fit.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SearchRateLimitInterceptor searchRateLimitInterceptor;
    private final AuthRateLimitInterceptor authRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(searchRateLimitInterceptor)
                .addPathPatterns("/search/**")
                .excludePathPatterns("/search/health", "/search/reindex/**");

        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/auth/check-email", "/auth/check-phone", "/auth/login");
    }
}
