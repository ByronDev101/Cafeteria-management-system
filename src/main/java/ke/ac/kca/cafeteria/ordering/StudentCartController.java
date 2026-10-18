package ke.ac.kca.cafeteria.ordering;

import jakarta.servlet.http.HttpSession;
import ke.ac.kca.cafeteria.config.PageContext;
import ke.ac.kca.cafeteria.menu.OrderableItem;
import ke.ac.kca.cafeteria.table.TableService;
import java.security.Principal;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The student's cart (FR-012). Students only; see SecurityConfig. Prices always come from the menu, never the browser. */
@Controller
public class StudentCartController {

    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final CartService carts;
    private final TableService tables;
    private final String zone;

    public StudentCartController(CartService carts, TableService tables,
                                 @Value("${app.display-timezone:Africa/Nairobi}") String zone) {
        this.carts = carts;
        this.tables = tables;
        this.zone = zone;
    }

    @GetMapping("/student/cart")
    public String cart(Principal principal, HttpSession session, Model model) {
        Cart cart = CartSession.get(session);
        model.addAttribute("cart", carts.view(cart));
        model.addAttribute("idempotencyKey", cart.idempotencyKey());
        model.addAttribute("paymentMethods", PaymentMethod.offered());
        model.addAttribute("welcome", PageContext.welcomeName(principal));
        model.addAttribute("today", PageContext.todayLabel(zone));
        tables.currentTable(session).ifPresent(table -> model.addAttribute("table", table));
        return "student/cart";
    }

    @PostMapping("/student/cart/add")
    public String add(@RequestParam String itemId, @RequestParam(defaultValue = "1") String quantity,
                      HttpSession session, RedirectAttributes redirect) {
        try {
            OrderableItem item = carts.add(CartSession.get(session), itemId, parseQuantity(quantity));
            redirect.addFlashAttribute("message", "Added " + item.name() + " to your cart.");
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        String anchor = UUID_TEXT.matcher(itemId).matches() ? "#item-" + itemId : "";
        return "redirect:/student/menu" + anchor;
    }

    @PostMapping("/student/cart/update")
    public String update(@RequestParam String itemId, @RequestParam String quantity, HttpSession session,
                         RedirectAttributes redirect) {
        try {
            carts.update(CartSession.get(session), itemId, parseQuantity(quantity));
        } catch (OrderException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return "redirect:/student/cart";
    }

    @PostMapping("/student/cart/remove")
    public String remove(@RequestParam String itemId, HttpSession session) {
        carts.remove(CartSession.get(session), itemId);
        return "redirect:/student/cart";
    }

    @PostMapping("/student/cart/clear")
    public String clear(HttpSession session) {
        CartSession.get(session).clear();
        return "redirect:/student/cart";
    }

    private static int parseQuantity(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new OrderException("Enter the quantity as a whole number.");
        }
    }
}
