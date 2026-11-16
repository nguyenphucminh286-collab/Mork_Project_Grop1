package vn.edu.fpt.savora.controller.publicpage;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class PageController {
    @GetMapping({"/", "/home"}) public String home(){return "public/home/view-home";}
    @GetMapping("/contact") public String contact(){return "public/contact/view-contact";}
}
