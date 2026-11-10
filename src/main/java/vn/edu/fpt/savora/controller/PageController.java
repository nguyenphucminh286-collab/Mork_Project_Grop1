package vn.edu.fpt.savora.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Điều hướng các đường dẫn URL cho phần Đăng nhập & Trang chủ khởi đầu dự án Savora
 * (Các thành viên tiếp theo sẽ bổ sung tiếp các route cho menu, reservation, staff, admin tại đây)
 */
@Controller
public class PageController {

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "forward:/public/auth/login.html";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "forward:/public/auth/register.html";
    }

    @GetMapping("/home")
    public String homePage() {
        return "forward:/public/home/view-home.html";
    }
}
