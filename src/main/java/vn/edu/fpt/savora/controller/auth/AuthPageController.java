package vn.edu.fpt.savora.controller.auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import vn.edu.fpt.savora.dto.request.LoginRequest;
import vn.edu.fpt.savora.dto.request.RegisterRequest;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.service.AuthService;
import vn.edu.fpt.savora.service.UserActivityTrackingService;
import vn.edu.fpt.savora.exception.AppException;

@Controller
@RequiredArgsConstructor
public class AuthPageController {
    private final AuthService authService;
    private final UserActivityTrackingService userActivityTrackingService;

    @GetMapping("/login")
    public String loginPage(Model model) {
        if (!model.containsAttribute("loginForm")) model.addAttribute("loginForm", new LoginRequest());
        return "public/auth/login";
    }
    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginRequest form,
                        BindingResult errors, Model model, HttpSession session, HttpServletRequest req) {
        if (errors.hasErrors()) return "public/auth/login";
        try {
            AuthUserResponse authUser = authService.login(form);
            storeUser(session, authUser);
            userActivityTrackingService.recordUserLogin(authUser, req);
            return "redirect:" + (authUser.getRedirectUrl() != null ? authUser.getRedirectUrl() : "/home");
        } catch (AppException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "public/auth/login";
        }
    }
    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registerForm")) model.addAttribute("registerForm", new RegisterRequest());
        return "public/auth/register";
    }
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterRequest form,
                           BindingResult errors, Model model, HttpSession session) {
        if (errors.hasErrors()) return "public/auth/register";
        try {
            storeUser(session, authService.register(form));
            return "redirect:/home";
        } catch (AppException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "public/auth/register";
        }
    }
    private void storeUser(HttpSession session, AuthUserResponse user) {
        session.setAttribute("userId", user.getUserId());
        session.setAttribute("roleId", user.getRoleId());
        session.setAttribute("roleName", user.getRoleName());
        session.setAttribute("fullName", user.getFullName());
        session.setAttribute("authUser", user);
    }
}
