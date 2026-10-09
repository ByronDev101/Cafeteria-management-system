package ke.ac.kca.cafeteria.menu;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The menu students browse (FR-006). Students only; see SecurityConfig. */
@Controller
public class StudentMenuController {

    private final MenuService service;

    public StudentMenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/student/menu")
    public String menu(Principal principal, Model model) {
        model.addAttribute("sections", service.studentMenu());
        model.addAttribute("name", principal.getName());
        return "student/menu";
    }
}
