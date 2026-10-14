package ke.ac.kca.cafeteria.table;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * How a student tells the system which table they are at: by scanning the QR code (GET /t/{token}) or by typing
 * the code printed under it. Both routes need a signed-in student, and a bad code always gets the same plain answer.
 */
@Controller
public class StudentTableController {

    static final String NOT_VALID = "That table code is not valid. Check the code on your table, or ask a member of staff.";

    private final TableService service;

    public StudentTableController(TableService service) {
        this.service = service;
    }

    @GetMapping("/t/{token}")
    public String scan(@PathVariable String token, HttpSession session, HttpServletResponse response) {
        TableResolution result = service.resolve(token);
        if (result.isValid()) {
            TableSession.store(session, result.table());
            return "redirect:/student/menu";
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return "student/table-invalid";
    }

    @GetMapping("/student/table")
    public String entryForm() {
        return "student/table-entry";
    }

    @PostMapping("/student/table")
    public String enter(@RequestParam String code, HttpSession session, Model model) {
        TableResolution result = service.resolve(code);
        if (result.isValid()) {
            TableSession.store(session, result.table());
            return "redirect:/student/menu";
        }
        model.addAttribute("errors", List.of(NOT_VALID));
        return "student/table-entry";
    }

    @PostMapping("/student/table/clear")
    public String clear(HttpSession session) {
        TableSession.clear(session);
        return "redirect:/student/menu";
    }
}
