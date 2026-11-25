package vn.edu.fpt.savora.controller.customer;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.repository.UserRepository;
import vn.edu.fpt.savora.service.ReservationService;

@Controller
@RequiredArgsConstructor
public class CustomerPageController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

    @GetMapping("/customer/reservations")
    public String myReservationsPage(HttpSession session, Model model) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        if (userId == null) {
            return "redirect:/login";
        }
        model.addAttribute("reservations", reservationService.getCustomerReservations(userId));
        return "customer/reservation/my-reservations";
    }

    @GetMapping("/customer/profile")
    public String customerProfilePage(HttpSession session, Model model) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        if (userId == null) {
            return "redirect:/login";
        }
        User user = userRepository.findById(userId).orElse(null);
        model.addAttribute("staffUser", user);
        return "staff/profile/waiter-profile";
    }
}
