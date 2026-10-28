package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.ordering.OrderException;
import java.security.Principal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The manager's credit page: set each student's limit and record payments against what they owe. Managers only; see
 * SecurityConfig and CreditService. The signed-in manager is always taken from the session, never from the form.
 */
@Controller
public class ManagerCreditController {

    private static final String TRY_AGAIN =
            "That was changed at the same moment by someone else. Please check the figures and try again.";

    private final CreditService credit;

    public ManagerCreditController(CreditService credit) {
        this.credit = credit;
    }

    @GetMapping("/manager/credit")
    public String page(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("rows", credit.overview(q));
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("maxLimitText", ke.ac.kca.cafeteria.menu.PriceParser.format(CreditService.MAX_LIMIT));
        return "manager/credit";
    }

    @PostMapping("/manager/credit/{id}/limit")
    public String setLimit(@PathVariable String id, @RequestParam(required = false) String limit,
                           @RequestParam(required = false) String q, Principal principal, RedirectAttributes redirect) {
        try {
            CreditRow row = credit.setLimit(principal.getName(), id, limit);
            redirect.addFlashAttribute("message", "Credit limit for " + row.getDisplayName() + " is now "
                    + row.getBalance().getLimitText() + ".");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            redirect.addFlashAttribute("errors", List.of(TRY_AGAIN));
        }
        return back(q, redirect);
    }

    @PostMapping("/manager/credit/{id}/settle")
    public String settle(@PathVariable String id, @RequestParam(required = false) String amount,
                         @RequestParam(required = false) String note, @RequestParam(required = false) String q,
                         Principal principal, RedirectAttributes redirect) {
        try {
            CreditRow row = credit.settle(principal.getName(), id, amount, note);
            redirect.addFlashAttribute("message", "Payment recorded for " + row.getDisplayName() + ". They now owe "
                    + row.getBalance().getOwedText() + ".");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            redirect.addFlashAttribute("errors", List.of(TRY_AGAIN));
        }
        return back(q, redirect);
    }

    private static String back(String q, RedirectAttributes redirect) {
        if (q != null && !q.isBlank()) {
            redirect.addAttribute("q", q.trim());
        }
        return "redirect:/manager/credit";
    }
}
