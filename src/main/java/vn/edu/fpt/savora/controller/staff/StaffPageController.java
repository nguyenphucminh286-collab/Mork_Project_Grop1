package vn.edu.fpt.savora.controller.staff;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.repository.UserRepository;

@Controller
@RequiredArgsConstructor
public class StaffPageController {

    private final UserRepository userRepository;

    @GetMapping({"/staff", "/staff/tables"})
    public String waiterTablesDashboard() {
        return "staff/table/waiter-tables";
    }

    @GetMapping({"/chef", "/staff/kitchen"})
    public String chefKitchenDashboard() {
        return "staff/kitchen/chef-kitchen";
    }

    @GetMapping("/staff/profile")
    public String waiterProfilePage(HttpSession session, Model model) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        if (userId == null) {
            return "redirect:/login";
        }
        User user = userRepository.findById(userId).orElse(null);
        model.addAttribute("staffUser", user);
        return "staff/profile/waiter-profile";
    }
}
