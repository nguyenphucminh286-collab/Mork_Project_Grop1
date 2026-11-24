package vn.edu.fpt.savora.config;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.service.AuthService;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthService authService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrationRepository) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**", "/staff/**", "/customer/**", "/api/admin/**",
                        "/api/staff/**", "/api/customer/**").permitAll()
                .anyRequest().permitAll());

        if (registrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth.loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
                    AuthUserResponse user = authService.loginWithOAuth2(principal);
                    HttpSession session = request.getSession();
                    session.setAttribute("userId", user.getUserId());
                    session.setAttribute("roleId", user.getRoleId());
                    session.setAttribute("roleName", user.getRoleName());
                    session.setAttribute("fullName", user.getFullName());
                    session.setAttribute("authUser", user);
                    response.sendRedirect("/home");
                }).failureUrl("/login?error=oauth2_failed"));
        }

        http.logout(logout -> logout.logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"));

        return http.build();
    }
}
