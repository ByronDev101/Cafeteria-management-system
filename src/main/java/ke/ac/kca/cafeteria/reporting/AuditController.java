package ke.ac.kca.cafeteria.reporting;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only audit log. Under /admin, so administrator only for now (role matrix still to be confirmed). */
@Controller
@RequestMapping("/admin/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", audit.recent(page));
        return "admin/audit";
    }
}
