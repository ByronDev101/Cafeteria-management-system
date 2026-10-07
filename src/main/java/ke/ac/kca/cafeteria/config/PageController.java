package ke.ac.kca.cafeteria.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

@Controller
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Sends each signed-in user to the area for their highest role. */
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (roles.contains("ROLE_ADMIN")) {
            return "redirect:/admin/home";
        }
        if (roles.contains("ROLE_MANAGER")) {
            return "redirect:/manager/home";
        }
        if (roles.contains("ROLE_STAFF")) {
            return "redirect:/staff/home";
        }
        if (roles.contains("ROLE_STUDENT")) {
            return "redirect:/student/home";
        }
        model.addAttribute("area", "No role assigned");
        model.addAttribute("name", authentication.getName());
        model.addAttribute("note", "Your account has no role yet. Ask the cafeteria administrator.");
        return "area";
    }
}
