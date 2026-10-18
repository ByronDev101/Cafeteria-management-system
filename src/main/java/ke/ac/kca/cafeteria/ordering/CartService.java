package ke.ac.kca.cafeteria.ordering;

import ke.ac.kca.cafeteria.menu.MenuService;
import ke.ac.kca.cafeteria.menu.OrderableItem;
import ke.ac.kca.cafeteria.menu.PriceParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Cart changes and the priced view of the cart (FR-012, FR-013, FR-016).
 * The cart stores only item IDs and quantities; every price shown or charged comes from the menu at that moment.
 */
@Service
public class CartService {

    private final MenuService menu;

    public CartService(MenuService menu) {
        this.menu = menu;
    }

    /** Adds an item that can be ordered right now. Returns it so the screen can say what was added. */
    public OrderableItem add(Cart cart, String itemId, int quantity) {
        OrderableItem item = menu.lookup(List.of(itemId)).get(itemId);
        if (item == null) {
            throw new OrderException("That item was not found.");
        }
        if (!item.orderable()) {
            throw new OrderException(item.name() + " is " + item.problem() + ".");
        }
        cart.add(itemId, quantity);
        return item;
    }

    public void update(Cart cart, String itemId, int quantity) {
        cart.setQuantity(itemId, quantity);
    }

    public void remove(Cart cart, String itemId) {
        cart.remove(itemId);
    }

    /** The cart priced from today's menu. Items that are no longer orderable are flagged, not silently dropped. */
    public CartView view(Cart cart) {
        if (cart == null || cart.isEmpty()) {
            return new CartView(List.of(), BigDecimal.ZERO.setScale(2), PriceParser.format(BigDecimal.ZERO.setScale(2)),
                    0, false);
        }
        Map<String, Integer> wanted = cart.lines();
        Map<String, OrderableItem> found = menu.lookup(wanted.keySet());

        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        boolean allOrderable = true;
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            OrderableItem item = found.get(entry.getKey());
            int quantity = entry.getValue();
            if (item == null) {
                allOrderable = false;
                lines.add(new CartLine(entry.getKey(), "Unknown item", "", quantity, "", false,
                        "no longer on the menu", null));
                continue;
            }
            BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2);
            if (item.orderable()) {
                total = total.add(lineTotal);
            } else {
                allOrderable = false;
            }
            lines.add(new CartLine(item.publicId(), item.name(), PriceParser.format(item.unitPrice()), quantity,
                    PriceParser.format(lineTotal), item.orderable(), item.problem(), item.imageVersion()));
        }
        return new CartView(lines, total, PriceParser.format(total), cart.unitCount(), allOrderable);
    }
}
