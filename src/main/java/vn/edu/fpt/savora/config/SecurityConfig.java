package vn.edu.fpt.savora.config;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.service.AuthService;

/**
 * Cấu hình bảo mật Spring Security & Đăng nhập bằng Google / Facebook OAuth2
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthService authService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/home",
                                "/index.html",
                                "/public/**",
                                "/assets/**",
                                "/components/**",
                                "/api/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/error",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                            AuthUserResponse authUser = authService.loginWithOAuth2(oauth2User);

                            HttpSession session = request.getSession();
                            session.setAttribute("userId", authUser.getUserId());
                            session.setAttribute("roleId", authUser.getRoleId());
                            session.setAttribute("roleName", authUser.getRoleName());
                            session.setAttribute("fullName", authUser.getFullName());
                            session.setAttribute("authUser", authUser);

                            response.sendRedirect(authUser.getRedirectUrl() != null ? authUser.getRedirectUrl() : "/home");
                        })
                        .failureUrl("/login?error=oauth2_failed")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .build();
    }
}
