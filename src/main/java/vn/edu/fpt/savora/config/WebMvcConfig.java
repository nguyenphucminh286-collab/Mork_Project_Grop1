package vn.edu.fpt.savora.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.fpt.savora.filter.AuthInterceptor;

/**
 * Cấu hình Web MVC cho Spring Boot:
 * 1. Hỗ trợ CORS khi chạy Frontend bằng Live Server (cổng 5500) trên VS Code hoặc chạy trực tiếp trên cổng 8080.
 * 2. Đăng ký AuthInterceptor kiểm tra phân quyền giống AuthFilter của SE2034-SWP391-G6.
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
                        "/error",
                        "/favicon.ico"
                );
    }
}
