package vn.edu.fpt.savora.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.fpt.savora.security.interceptor.AuthInterceptor;

/**
 * Spring Web MVC configuration:
 * 1. Configures CORS for local frontend development (Live Server port 5500) and port 8080.
 * 2. Registers AuthInterceptor for session-based authentication and role authorization.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/assets/**",
                        "/components/**",
                        "/public/**",
                        "/api/public/**",
                        "/api/auth/**",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/error",
                        "/favicon.ico"
                );
    }
}
