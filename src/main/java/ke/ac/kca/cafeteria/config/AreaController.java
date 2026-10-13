package ke.ac.kca.cafeteria.config;

import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing pages that prove role-based access, with links to the features built so far. */
@Controller
public class AreaController {

    private final String zone;

    public AreaController(@Value("${app.display-timezone:Africa/Nairobi}") String zone) {
        this.zone = zone;
    }

    @GetMapping("/student/home")
    public String student(Principal principal, Model model) {
        model.addAttribute("studentLinks", true);
        return area("Student area", "Ordering arrives in Sprint 3.", principal, model);
    }

    @GetMapping("/staff/home")
    public String staff(Principal principal, Model model) {
        return area("Staff area", "The order queue arrives in Sprint 4.", principal, model);
    }

    @GetMapping("/manager/home")
    public String manager(Principal principal, Model model) {
        model.addAttribute("managerLinks", true);
        return area("Manager area", "Manage the menu. Reports arrive in later sprints.", principal, model);
    }

    @GetMapping("/admin/home")
    public String admin(Principal principal, Model model) {
        model.addAttribute("adminLinks", true);
        return area("Administrator area", "Manage accounts and review the audit log.", principal, model);
    }

    private String area(String title, String note, Principal principal, Model model) {
        model.addAttribute("area", title);
        model.addAttribute("note", note);
        model.addAttribute("welcome", PageContext.welcomeName(principal));
        model.addAttribute("today", PageContext.todayLabel(zone));
        return "area";
    }
}
