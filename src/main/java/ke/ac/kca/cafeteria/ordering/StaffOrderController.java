package ke.ac.kca.cafeteria.ordering;

import ke.ac.kca.cafeteria.finance.PaymentResult;
import ke.ac.kca.cafeteria.finance.PaymentService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The order board for staff and managers (FR-019 to FR-021). The person who makes a change is always taken from the
 * signed-in session, and each button sends back the order's version so a stale screen cannot overwrite newer work.
 */
@Controller
public class StaffOrderController {

    private final OrderProcessingService processing;
    private final PaymentService payments;

    public StaffOrderController(OrderProcessingService processing, PaymentService payments) {
        this.processing = processing;
        this.payments = payments;
    }

    @GetMapping("/staff/orders")
    public String board(@RequestParam(required = false) String filter, Model model) {
        QueueFilter selected = QueueFilter.parse(filter);
        model.addAttribute("filter", selected);
        model.addAttribute("filters", QueueFilter.values());
        model.addAttribute("counts", processing.counts());
        model.addAttribute("orders", processing.queue(selected));
        model.addAttribute("token", processing.pollToken());
        return "staff/orders";
    }

    @PostMapping("/staff/orders/{id}/transition")
    public String transition(@PathVariable String id,
                             @RequestParam String to,
                             @RequestParam(required = false) String version,
                             @RequestParam(required = false) String reason,
                             @RequestParam(required = false) String filter,
                             Principal principal, RedirectAttributes redirect) {
        try {
            Long expected = parseVersion(version);
            TransitionResult result = processing.transition(principal.getName(), id, to, reason, expected);
            redirect.addFlashAttribute("message",
                    "Order " + result.orderNumber() + " is now " + result.newStatus().getLabel().toLowerCase() + ".");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("errors",
                    List.of("That order was changed at the same moment by someone else. The board has been refreshed."));
        }
        return "redirect:/staff/orders?filter=" + QueueFilter.parse(filter).name();
    }

    /** Staff write down that the payment for an order has been received. The reference (an M-Pesa code) is optional. */
    @PostMapping("/staff/orders/{id}/payment/verify")
    public String paymentReceived(@PathVariable String id,
                                  @RequestParam(required = false) String reference,
                                  @RequestParam(required = false) String filter,
                                  Principal principal, RedirectAttributes redirect) {
        try {
            PaymentResult result = payments.verify(principal.getName(), id, reference);
            redirect.addFlashAttribute("message",
                    "Payment for order " + result.orderNumber() + " marked as received.");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("errors",
                    List.of("That payment was changed at the same moment by someone else. The board has been refreshed."));
        }
        return "redirect:/staff/orders?filter=" + QueueFilter.parse(filter).name();
    }

    /** Staff write down that a refund has been handed back. */
    @PostMapping("/staff/orders/{id}/payment/refunded")
    public String refundPaid(@PathVariable String id,
                             @RequestParam(required = false) String filter,
                             Principal principal, RedirectAttributes redirect) {
        try {
            PaymentResult result = payments.markRefunded(principal.getName(), id);
            redirect.addFlashAttribute("message",
                    "Refund for order " + result.orderNumber() + " marked as paid.");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("errors",
                    List.of("That payment was changed at the same moment by someone else. The board has been refreshed."));
        }
        return "redirect:/staff/orders?filter=" + QueueFilter.parse(filter).name();
    }

    /** A tiny answer that changes whenever any order changes; the board reloads itself when it differs. */
    @GetMapping("/staff/orders/poll")
    @ResponseBody
    public Map<String, String> poll() {
        return Map.of("token", processing.pollToken());
    }

    private static Long parseVersion(String text) {
        try {
            return Long.valueOf(text.trim());
        } catch (RuntimeException e) {
            throw new OrderException("This page is out of date. Reload the board and try again.");
        }
    }
}
