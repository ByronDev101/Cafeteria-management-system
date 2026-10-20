package ke.ac.kca.cafeteria.menu;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lets kitchen staff mark an item sold out or back on, and nothing else: no prices, names or photos (those stay with
 * managers). Every change is audited. Staff and managers only; see SecurityConfig.
 */
@Controller
public class StaffAvailabilityController {

    private static final String LIST = "redirect:/staff/availability";

    private final MenuService service;

    public StaffAvailabilityController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/staff/availability")
    public String list(Model model) {
        model.addAttribute("categories", service.managerMenu());
        return "staff/availability";
    }

    @PostMapping("/staff/availability/{id}/available")
    public String available(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return change(id, true, principal, redirect);
    }

    @PostMapping("/staff/availability/{id}/unavailable")
    public String unavailable(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return change(id, false, principal, redirect);
    }

    private String change(String id, boolean available, Principal principal, RedirectAttributes redirect) {
        try {
            service.setItemAvailable(principal.getName(), id, available);
            redirect.addFlashAttribute("message", available ? "Item is back on the menu." : "Item marked sold out.");
        } catch (MenuException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }
}
