package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.reporting.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The rules that decide whether an item can go into an order right now. */
class MenuLookupTest {

    private MenuItemRepository items;
    private MenuService service;
    private MenuCategory drinks;

    @BeforeEach
    void setUp() {
        items = mock(MenuItemRepository.class);
        service = new MenuService(mock(MenuCategoryRepository.class), items, mock(MenuImageStore.class), mock(AuditService.class));
        drinks = new MenuCategory("Drinks", null, 1);
    }

    private MenuItem item(String name) {
        MenuItem item = new MenuItem(drinks, name, null, new BigDecimal("50.00"));
        when(items.findByPublicId(item.getPublicId())).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void anAvailableActiveItemInAnActiveCategoryCanBeOrderedAtTodaysPrice() {
        MenuItem tea = item("Tea");
        OrderableItem found = service.lookup(List.of(tea.getPublicId())).get(tea.getPublicId());
        assertTrue(found.orderable());
        assertNull(found.problem());
        assertEquals(new BigDecimal("50.00"), found.unitPrice());
        assertEquals("Tea", found.name());
    }

    @Test
    void aSoldOutItemCannotBeOrdered() {
        MenuItem juice = item("Fresh Juice");
        juice.setAvailable(false);
        OrderableItem found = service.lookup(List.of(juice.getPublicId())).get(juice.getPublicId());
        assertFalse(found.orderable());
        assertEquals("sold out", found.problem());
    }

    @Test
    void aDeactivatedItemOrADeactivatedCategoryIsNoLongerOnTheMenu() {
        MenuItem old = item("Old Soda");
        old.setActive(false);
        assertEquals("no longer on the menu", service.lookup(List.of(old.getPublicId())).get(old.getPublicId()).problem());

        MenuItem tea = item("Tea");
        drinks.setActive(false);
        OrderableItem found = service.lookup(List.of(tea.getPublicId())).get(tea.getPublicId());
        assertFalse(found.orderable());
        assertEquals("no longer on the menu", found.problem());
    }

    @Test
    void unknownIdsAreSimplyMissing() {
        when(items.findByPublicId("ghost")).thenReturn(Optional.empty());
        Map<String, OrderableItem> found = service.lookup(List.of("ghost"));
        assertTrue(found.isEmpty());
    }
}
