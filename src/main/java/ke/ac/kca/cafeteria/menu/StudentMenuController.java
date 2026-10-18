package ke.ac.kca.cafeteria.menu;

import jakarta.servlet.http.HttpSession;
import ke.ac.kca.cafeteria.config.PageContext;
import ke.ac.kca.cafeteria.ordering.CartService;
import ke.ac.kca.cafeteria.ordering.CartSession;
import ke.ac.kca.cafeteria.ordering.OrderService;
import ke.ac.kca.cafeteria.table.TableService;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The menu students browse (FR-006), shown with their table, their cart and any orders still in progress.
 * Students only; see SecurityConfig.
 */
@Controller
public class StudentMenuController {

    private final MenuService service;
    private final TableService tables;
    private final CartService carts;
    private final OrderService orders;
    private final String zone;

    public StudentMenuController(MenuService service, TableService tables, CartService carts, OrderService orders,
                                 @Value("${app.display-timezone:Africa/Nairobi}") String zone) {
        this.service = service;
        this.tables = tables;
        this.carts = carts;
        this.orders = orders;
        this.zone = zone;
    }

    @GetMapping("/student/menu")
    public String menu(Principal principal, HttpSession session, Model model) {
        List<CategoryView> sections = service.studentMenu();
        model.addAttribute("sections", sections);
        model.addAttribute("featured", MenuService.featured(sections));
        model.addAttribute("itemCount", sections.stream().mapToInt(s -> s.getItems().size()).sum());
        model.addAttribute("welcome", PageContext.welcomeName(principal));
        model.addAttribute("today", PageContext.todayLabel(zone));
        model.addAttribute("cart", carts.view(CartSession.peek(session)));
        model.addAttribute("activeOrders", orders.activeOrders(principal.getName()));
        tables.currentTable(session).ifPresent(table -> model.addAttribute("table", table));
        return "student/menu";
    }
}
