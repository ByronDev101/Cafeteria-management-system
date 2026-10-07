package ke.ac.kca.cafeteria.config;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Temporary landing pages that prove role-based access. Replaced by real feature pages in later sprints. */
@Controller
public class AreaController {

    @GetMapping("/student/home")
    public String student(Principal principal, Model model) {
        return area("Student area", "The menu and ordering arrive in Sprints 2 and 3.", principal, model);
    }

    @GetMapping("/staff/home")
    public String staff(Principal principal, Model model) {
        return area("Staff area", "The order queue arrives in Sprint 4.", principal, model);
    }

    @GetMapping("/manager/home")
    public String manager(Principal principal, Model model) {
        return area("Manager area", "Menu management and reports arrive in later sprints.", principal, model);
    }

    @GetMapping("/admin/home")
    public String admin(Principal principal, Model model) {
        return area("Administrator area", "Account management arrives in Slice B of this sprint.", principal, model);
    }

    private String area(String title, String note, Principal principal, Model model) {
        model.addAttribute("area", title);
        model.addAttribute("note", note);
        model.addAttribute("name", principal.getName());
        return "area";
    }
}
