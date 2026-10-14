package ke.ac.kca.cafeteria.table;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrator screens for tables and their QR codes (FR-009). Administrators only; see SecurityConfig. */
@Controller
@RequestMapping("/admin/tables")
public class AdminTableController {

    private static final String LIST = "redirect:/admin/tables";
    private static final int QR_PIXELS = 360;

    private final TableService service;
    private final PublicUrl publicUrl;

    public AdminTableController(TableService service, PublicUrl publicUrl) {
        this.service = service;
        this.publicUrl = publicUrl;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tables", service.list());
        return "admin/tables";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        return form(model, "New table", "/admin/tables", "", null);
    }

    @PostMapping
    public String create(@RequestParam String label, Principal principal, HttpServletRequest request,
                         Model model, RedirectAttributes redirect) {
        try {
            IssuedQr issued = service.create(principal.getName(), label);
            redirect.addFlashAttribute("issued", sheet(issued, request));
            redirect.addFlashAttribute("message", "Table created. Print the QR code below now: it is shown only once.");
            return LIST;
        } catch (TableException e) {
            return form(model, "New table", "/admin/tables", label, e.getMessages());
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            TableView table = service.get(id);
            return form(model, "Edit table", "/admin/tables/" + id, table.getLabel(), null);
        } catch (TableException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
            return LIST;
        }
    }

    @PostMapping("/{id}")
    public String rename(@PathVariable String id, @RequestParam String label, Principal principal,
                         Model model, RedirectAttributes redirect) {
        try {
            service.rename(principal.getName(), id, label);
            redirect.addFlashAttribute("message", "Table saved.");
            return LIST;
        } catch (TableException e) {
            return form(model, "Edit table", "/admin/tables/" + id, label, e.getMessages());
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        try {
            service.deactivate(principal.getName(), id);
            redirect.addFlashAttribute("message",
                    "Table deactivated. Its old QR code no longer works, even if you reactivate the table.");
        } catch (TableException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable String id, Principal principal, HttpServletRequest request,
                             RedirectAttributes redirect) {
        try {
            IssuedQr issued = service.reactivate(principal.getName(), id);
            redirect.addFlashAttribute("issued", sheet(issued, request));
            redirect.addFlashAttribute("message", "Table reactivated with a new QR code. Print it now: it is shown only once.");
        } catch (TableException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }

    @PostMapping("/{id}/rotate")
    public String rotate(@PathVariable String id, Principal principal, HttpServletRequest request,
                         RedirectAttributes redirect) {
        try {
            IssuedQr issued = service.rotate(principal.getName(), id);
            redirect.addFlashAttribute("issued", sheet(issued, request));
            redirect.addFlashAttribute("message", "New QR code issued. The old one no longer works. Print the new one now.");
        } catch (TableException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }

    private QrSheet sheet(IssuedQr issued, HttpServletRequest request) {
        String url = publicUrl.tableUrl(request, issued.token());
        return new QrSheet(issued.label(), TableToken.display(issued.token()), url,
                QrCodeImage.pngDataUri(url, QR_PIXELS));
    }

    private String form(Model model, String title, String action, String label, java.util.List<String> errors) {
        model.addAttribute("title", title);
        model.addAttribute("formAction", action);
        model.addAttribute("label", label);
        if (errors != null) {
            model.addAttribute("errors", errors);
        }
        return "admin/table-form";
    }
}
