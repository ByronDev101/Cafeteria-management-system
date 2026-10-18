package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.menu.MenuService;
import ke.ac.kca.cafeteria.menu.OrderableItem;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartServiceTest {

    private MenuService menu;
    private CartService service;

    private static final OrderableItem TEA = new OrderableItem(1L, "tea", "Tea", new BigDecimal("50.00"), true, null, 7L);
    private static final OrderableItem SAMOSA = new OrderableItem(2L, "samosa", "Samosa", new BigDecimal("30.00"), true, null, null);
    private static final OrderableItem JUICE = new OrderableItem(3L, "juice", "Fresh Juice", new BigDecimal("120.00"), false, "sold out", null);

    @BeforeEach
    void setUp() {
        menu = mock(MenuService.class);
        service = new CartService(menu);
    }

    private void menuHas(OrderableItem... items) {
        Map<String, OrderableItem> found = new LinkedHashMap<>();
        for (OrderableItem item : items) {
            found.put(item.publicId(), item);
        }
        when(menu.lookup(any())).thenReturn(found);
    }

    @Test
    void addingAnAvailableItemPutsItInTheCart() {
        menuHas(TEA);
        Cart cart = new Cart();
        OrderableItem added = service.add(cart, "tea", 2);
        assertEquals("Tea", added.name());
        assertEquals(2, cart.lines().get("tea"));
    }

    @Test
    void soldOutOrUnknownItemsCannotBeAdded() {
        menuHas(JUICE);
        Cart cart = new Cart();
        OrderException soldOut = assertThrows(OrderException.class, () -> service.add(cart, "juice", 1));
        assertEquals("Fresh Juice is sold out.", soldOut.getMessage());
        OrderException unknown = assertThrows(OrderException.class, () -> service.add(cart, "ghost", 1));
        assertEquals("That item was not found.", unknown.getMessage());
        assertTrue(cart.isEmpty());
    }

    @Test
    void theQuantityLimitStillApplies() {
        menuHas(TEA);
        Cart cart = new Cart();
        service.add(cart, "tea", 8);
        assertThrows(OrderException.class, () -> service.add(cart, "tea", 3));
        assertEquals(8, cart.lines().get("tea"));
    }

    @Test
    void theCartIsPricedFromTheMenuNotFromTheCart() {
        menuHas(TEA, SAMOSA);
        Cart cart = new Cart();
        cart.add("tea", 2);
        cart.add("samosa", 1);
        CartView view = service.view(cart);
        assertEquals(2, view.getLines().size());
        assertEquals(new BigDecimal("130.00"), view.getTotal());
        assertEquals("KSh 130.00", view.getTotalText());
        assertEquals(3, view.getUnitCount());
        assertTrue(view.isCanPlace());
        assertEquals("KSh 100.00", view.getLines().get(0).getLineTotalText());
        assertEquals(7L, view.getLines().get(0).getImageVersion());
    }

    @Test
    void anItemThatSoldOutIsFlaggedAndStopsTheOrderButStaysVisible() {
        menuHas(TEA, JUICE);
        Cart cart = new Cart();
        cart.add("tea", 1);
        cart.add("juice", 1);
        CartView view = service.view(cart);
        assertEquals(2, view.getLines().size());
        assertFalse(view.getLines().get(1).isOrderable());
        assertEquals("sold out", view.getLines().get(1).getProblem());
        assertEquals(new BigDecimal("50.00"), view.getTotal());
        assertFalse(view.isCanPlace());
    }

    @Test
    void anItemRemovedFromTheMenuIsFlagged() {
        menuHas(TEA);
        Cart cart = new Cart();
        cart.add("tea", 1);
        cart.add("deleted-item", 1);
        CartView view = service.view(cart);
        assertFalse(view.isCanPlace());
        assertEquals("no longer on the menu", view.getLines().get(1).getProblem());
    }

    @Test
    void emptyOrMissingCartsGiveAnEmptyView() {
        assertTrue(service.view(null).isEmpty());
        assertTrue(service.view(new Cart()).isEmpty());
        assertFalse(service.view(new Cart()).isCanPlace());
        assertEquals("KSh 0.00", service.view(null).getTotalText());
    }

    @Test
    void updateAndRemoveChangeTheCart() {
        Cart cart = new Cart();
        cart.add("tea", 2);
        service.update(cart, "tea", 4);
        assertEquals(4, cart.lines().get("tea"));
        service.remove(cart, "tea");
        assertTrue(cart.isEmpty());
        assertEquals(List.of(), List.copyOf(cart.lines().keySet()));
    }
}
