package ke.ac.kca.cafeteria.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Any signed-in user changes their own password here. */
@Controller
@RequestMapping("/account/password")
public class AccountController {

    private final UserAdminService service;

    public AccountController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public String form() {
        return "account/password";
    }

    @PostMapping
    public String change(@RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         Principal principal, HttpServletRequest request, Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errors", List.of("The two new passwords do not match."));
            return "account/password";
        }
        try {
            service.changeOwnPassword(principal.getName(), currentPassword, newPassword);
        } catch (UserAdminException e) {
            model.addAttribute("errors", e.getMessages());
            return "account/password";
        }
        // End the session so the person signs in again with the new password.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login?changed";
    }
}
