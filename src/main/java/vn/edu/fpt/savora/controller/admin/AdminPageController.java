package vn.edu.fpt.savora.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/dashboard"})
    public String adminDashboardPage() {
        return "admin/dashboard/admin-dashboard";
    }
}
