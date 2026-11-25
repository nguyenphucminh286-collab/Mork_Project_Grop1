package vn.edu.fpt.savora.controller.publicpage;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.fpt.savora.service.ReservationService;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final ReservationService reservationService;

    @GetMapping({"/", "/home"})
    public String home(HttpSession session, Model model) {
        if (session != null) {
            Integer roleId = (Integer) session.getAttribute("roleId");
            String roleName = (String) session.getAttribute("roleName");
            if (Integer.valueOf(1).equals(roleId) || "ADMIN".equalsIgnoreCase(roleName)) {
                return "redirect:/admin/dashboard";
            }
            if (Integer.valueOf(3).equals(roleId) || "STAFF".equalsIgnoreCase(roleName)) {
                return "redirect:/staff/tables";
            }
            if (Integer.valueOf(4).equals(roleId) || "CHEF".equalsIgnoreCase(roleName)) {
                return "redirect:/staff/kitchen";
            }
        }
        try {
            model.addAttribute("dishes", reservationService.getAvailableDishesFromDb());
            model.addAttribute("vouchers", reservationService.getActiveVouchers());
            model.addAttribute("tables", reservationService.getRealTimeTableAvailability(
                    LocalDate.now(), LocalTime.of(19, 0), 2, 0, "ALL"
            ));
        } catch (Exception ignored) {
        }
        return "public/home/view-home";
    }

    @GetMapping("/contact")
    public String contact() {
        return "public/contact/view-contact";
    }
}
