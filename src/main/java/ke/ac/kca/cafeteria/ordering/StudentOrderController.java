package ke.ac.kca.cafeteria.ordering;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ke.ac.kca.cafeteria.config.PageContext;
import ke.ac.kca.cafeteria.table.SelectedTable;
import ke.ac.kca.cafeteria.table.TableService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Placing and following orders (FR-014 to FR-022). Students only. The student is always taken from the signed-in
 * session, and every lookup is ownership-checked, so one student can never see or touch another's order.
 */
@Controller
public class StudentOrderController {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final OrderService orders;
    private final TableService tables;
    private final String zone;

    public StudentOrderController(OrderService orders, TableService tables,
                                  @Value("${app.display-timezone:Africa/Nairobi}") String zone) {
        this.orders = orders;
        this.tables = tables;
        this.zone = zone;
    }

    @PostMapping("/student/orders")
    public String place(@RequestParam(required = false) String paymentMethod,
                        @RequestParam(required = false) String idempotencyKey,
                        Principal principal, HttpSession session, RedirectAttributes redirect) {
        Cart cart = CartSession.get(session);
        String key = idempotencyKey != null && KEY.matcher(idempotencyKey).matches()
                ? idempotencyKey : cart.idempotencyKey();
        String tableId = tables.currentTable(session).map(SelectedTable::getPublicId).orElse(null);
        PaymentMethod method = PaymentMethod.parse(paymentMethod).orElse(null);
        try {
            PlacedOrder placed = orders.place(principal.getName(), cart.lines(), tableId, method, key);
            if (!placed.duplicate()) {
                cart.clear();
            }
            redirect.addFlashAttribute("message", placed.duplicate()
                    ? "That order was already placed. Here it is."
                    : "Order " + placed.orderNumber() + " placed. Staff will accept it shortly.");
            return "redirect:/student/orders/" + placed.publicId();
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
            return "redirect:/student/cart";
        } catch (DataIntegrityViolationException e) {
            // Two taps at the same moment: the other one won, so show that order instead of a second one.
            Optional<PlacedOrder> existing = orders.findByKey(principal.getName(), key);
            if (existing.isPresent()) {
                return "redirect:/student/orders/" + existing.get().publicId();
            }
            redirect.addFlashAttribute("errors", List.of("Something went wrong placing your order. Please try again."));
            return "redirect:/student/cart";
        }
    }

    @GetMapping("/student/orders")
    public String history(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("page", orders.history(principal.getName(), page));
        header(principal, model);
        return "student/orders";
    }

    @GetMapping("/student/orders/{id}")
    public String detail(@PathVariable String id, Principal principal, HttpServletResponse response, Model model) {
        header(principal, model);
        try {
            model.addAttribute("order", orders.detail(principal.getName(), id));
            return "student/order";
        } catch (OrderException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "student/order-not-found";
        }
    }

    /** Tiny JSON answer for the page's quiet refresh. Only the signed-in owner can ask. */
    @GetMapping("/student/orders/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id, Principal principal) {
        try {
            OrderStatus status = orders.statusOf(principal.getName(), id);
            return ResponseEntity.ok(Map.of("status", status.name(), "label", status.getLabel(),
                    "terminal", status.isTerminal()));
        } catch (OrderException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/student/orders/{id}/cancel")
    public String cancel(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        try {
            orders.cancelOwn(principal.getName(), id);
            redirect.addFlashAttribute("message", "Your order has been cancelled.");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("errors",
                    List.of("The order changed while you were cancelling it. Please check its status below."));
        }
        return "redirect:/student/orders/" + id;
    }

    private void header(Principal principal, Model model) {
        model.addAttribute("welcome", PageContext.welcomeName(principal));
        model.addAttribute("today", PageContext.todayLabel(zone));
    }
}
