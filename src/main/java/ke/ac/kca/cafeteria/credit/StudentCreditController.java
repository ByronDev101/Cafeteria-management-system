package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.config.PageContext;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The student's own credit page: limit, what is held and owed, and recent ledger lines. Students only. */
@Controller
public class StudentCreditController {

    private final CreditService credit;
    private final String zone;

    public StudentCreditController(CreditService credit,
                                   @Value("${app.display-timezone:Africa/Nairobi}") String zone) {
        this.credit = credit;
        this.zone = zone;
    }

    @GetMapping("/student/credit")
    public String page(Principal principal, Model model) {
        model.addAttribute("balance", credit.balanceOf(principal.getName()));
        model.addAttribute("entries", credit.recentEntries(principal.getName()));
        model.addAttribute("welcome", PageContext.welcomeName(principal));
        model.addAttribute("today", PageContext.todayLabel(zone));
        return "student/credit";
    }
}
