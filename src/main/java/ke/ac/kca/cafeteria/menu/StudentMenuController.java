package ke.ac.kca.cafeteria.menu;

import ke.ac.kca.cafeteria.config.PageContext;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The menu students browse (FR-006). Students only; see SecurityConfig. */
@Controller
public class StudentMenuController {

    private final MenuService service;
    private final String zone;

    public StudentMenuController(MenuService service,
                                 @Value("${app.display-timezone:Africa/Nairobi}") String zone) {
        this.service = service;
        this.zone = zone;
    }

    @GetMapping("/student/menu")
    public String menu(Principal principal, Model model) {
        List<CategoryView> sections = service.studentMenu();
        model.addAttribute("sections", sections);
        model.addAttribute("featured", MenuService.featured(sections));
        model.addAttribute("itemCount", sections.stream().mapToInt(s -> s.getItems().size()).sum());
        model.addAttribute("welcome", PageContext.welcomeName(principal));
        model.addAttribute("today", PageContext.todayLabel(zone));
        return "student/menu";
    }
}
