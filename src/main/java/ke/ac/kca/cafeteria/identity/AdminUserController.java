package ke.ac.kca.cafeteria.identity;

import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrator screens for accounts (SDS: /admin/users/**, administrator only). */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final String LIST = "redirect:/admin/users";

    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("page", service.list(page, principal.getName()));
        return "admin/users";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("roleNames", UserAdminService.ROLE_NAMES);
        model.addAttribute("selected", List.of("STUDENT"));
        return "admin/user-new";
    }

    @PostMapping
    public String create(@RequestParam String username,
                         @RequestParam String displayName,
                         @RequestParam(required = false) List<String> roles,
                         Principal principal, RedirectAttributes redirect, Model model) {
        try {
            CreatedAccount created = service.createUser(principal.getName(), username, displayName, roles);
            redirect.addFlashAttribute("newUsername", created.username());
            redirect.addFlashAttribute("newPassword", created.temporaryPassword());
            return LIST;
        } catch (UserAdminException e) {
            model.addAttribute("errors", e.getMessages());
            model.addAttribute("roleNames", UserAdminService.ROLE_NAMES);
            model.addAttribute("username", username);
            model.addAttribute("displayName", displayName);
            model.addAttribute("selected", roles == null ? List.of() : roles);
            return "admin/user-new";
        }
    }

    @PostMapping("/{publicId}/activate")
    public String activate(@PathVariable String publicId, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Account activated.", () -> service.setActive(principal.getName(), publicId, true));
    }

    @PostMapping("/{publicId}/deactivate")
    public String deactivate(@PathVariable String publicId, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Account deactivated.", () -> service.setActive(principal.getName(), publicId, false));
    }

    @PostMapping("/{publicId}/unlock")
    public String unlock(@PathVariable String publicId, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Account unlocked.", () -> service.unlock(principal.getName(), publicId));
    }

    @PostMapping("/{publicId}/reset-password")
    public String resetPassword(@PathVariable String publicId, Principal principal, RedirectAttributes redirect) {
        try {
            CreatedAccount reset = service.resetPassword(principal.getName(), publicId);
            redirect.addFlashAttribute("newUsername", reset.username());
            redirect.addFlashAttribute("newPassword", reset.temporaryPassword());
        } catch (UserAdminException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }

    @GetMapping("/{publicId}/roles")
    public String rolesForm(@PathVariable String publicId, Principal principal,
                            Model model, RedirectAttributes redirect) {
        try {
            UserRow user = service.details(publicId, principal.getName());
            model.addAttribute("user", user);
            model.addAttribute("roleNames", UserAdminService.ROLE_NAMES);
            model.addAttribute("selected", user.getRoleList());
            return "admin/user-roles";
        } catch (UserAdminException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
            return LIST;
        }
    }

    @PostMapping("/{publicId}/roles")
    public String saveRoles(@PathVariable String publicId,
                            @RequestParam(required = false) List<String> roles,
                            Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Roles updated.", () -> service.setRoles(principal.getName(), publicId, roles));
    }

    private String run(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("message", success);
        } catch (UserAdminException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }
}
