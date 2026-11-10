package vn.edu.fpt.savora.controller.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.dto.request.LoginRequest;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.service.AuthService;

/**
 * REST API xác thực người dùng (Đăng nhập Email/Mật khẩu + Google/Facebook OAuth2 + Đăng xuất)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        AuthUserResponse authUser = authService.login(request);
        session.setAttribute("userId", authUser.getUserId());
        session.setAttribute("roleId", authUser.getRoleId());
        session.setAttribute("roleName", authUser.getRoleName());
        session.setAttribute("fullName", authUser.getFullName());
        session.setAttribute("authUser", authUser);
        return ApiResponse.ok("Signed in successfully!", authUser);
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> getCurrentUser(HttpSession session, Authentication authentication) {
        AuthUserResponse authUser = (AuthUserResponse) session.getAttribute("authUser");
        if (authUser == null && authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            authUser = authService.loginWithOAuth2(oauth2User);
            session.setAttribute("authUser", authUser);
            session.setAttribute("userId", authUser.getUserId());
            session.setAttribute("roleId", authUser.getRoleId());
            session.setAttribute("roleName", authUser.getRoleName());
            session.setAttribute("fullName", authUser.getFullName());
        }
        if (authUser == null) {
            return ApiResponse.error("Not signed in");
        }
        return ApiResponse.ok(authUser);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok("Signed out successfully", null);
    }
}
