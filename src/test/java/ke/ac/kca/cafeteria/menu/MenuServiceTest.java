package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.reporting.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MenuServiceTest {

    private MenuCategoryRepository categories;
    private MenuItemRepository items;
    private AuditService audit;
    private MenuService service;

    @BeforeEach
    void setUp() {
        categories = mock(MenuCategoryRepository.class);
        items = mock(MenuItemRepository.class);
        audit = mock(AuditService.class);
        service = new MenuService(categories, items, audit);
    }

    private MenuCategory category(String name) {
        MenuCategory category = new MenuCategory(name, null, 1);
        when(categories.findByPublicId(category.getPublicId())).thenReturn(Optional.of(category));
        return category;
    }

    private MenuItem item(MenuCategory category, String name, String price) {
        MenuItem item = new MenuItem(category, name, "desc", new BigDecimal(price).setScale(2));
        when(items.findByPublicId(item.getPublicId())).thenReturn(Optional.of(item));
        return item;
    }

    // ---- categories ----

    @Test
    void createCategorySavesAndAudits() {
        service.createCategory("mgr", "  Drinks ", "Hot and cold", "2");
        ArgumentCaptor<MenuCategory> saved = ArgumentCaptor.forClass(MenuCategory.class);
        verify(categories).save(saved.capture());
        assertEquals("Drinks", saved.getValue().getName());
        assertEquals(2, saved.getValue().getDisplayOrder());
        verify(audit).record(eq("mgr"), eq("MENU_CATEGORY_CREATED"), eq("MENU_CATEGORY"),
                eq(saved.getValue().getPublicId()), eq("SUCCESS"), anyString());
    }

    @Test
    void createCategoryRejectsDuplicateNameAndBadInput() {
        when(categories.existsByNameIgnoreCase("Drinks")).thenReturn(true);
        assertThrows(MenuException.class, () -> service.createCategory("mgr", "Drinks", "", "1"));
        assertThrows(MenuException.class, () -> service.createCategory("mgr", "x", "", "1"));
        assertThrows(MenuException.class, () -> service.createCategory("mgr", "Snacks", "", "abc"));
        assertThrows(MenuException.class, () -> service.createCategory("mgr", "Snacks", "", "-1"));
        verify(categories, never()).save(any());
    }

    @Test
    void deactivatingACategoryIsAudited() {
        MenuCategory drinks = category("Drinks");
        service.setCategoryActive("mgr", drinks.getPublicId(), false);
        assertFalse(drinks.isActive());
        verify(audit).record(eq("mgr"), eq("MENU_CATEGORY_DEACTIVATED"), eq("MENU_CATEGORY"),
                eq(drinks.getPublicId()), eq("SUCCESS"), anyString());
    }

    // ---- items ----

    @Test
    void createItemStoresPriceWithTwoDecimalsAndAudits() {
        MenuCategory meals = category("Meals");
        service.createItem("mgr", meals.getPublicId(), "Chicken and Rice", "Roast chicken", "350");
        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(items).save(saved.capture());
        assertEquals(new BigDecimal("350.00"), saved.getValue().getPrice());
        assertTrue(saved.getValue().isAvailable());
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_CREATED"), eq("MENU_ITEM"),
                eq(saved.getValue().getPublicId()), eq("SUCCESS"), argThat(d -> d.contains("350.00")));
    }

    @Test
    void createItemRejectsBadPriceDuplicateAndInactiveCategory() {
        MenuCategory meals = category("Meals");
        assertThrows(MenuException.class,
                () -> service.createItem("mgr", meals.getPublicId(), "Stew", "", "-5"));
        assertThrows(MenuException.class,
                () -> service.createItem("mgr", meals.getPublicId(), "Stew", "", "abc"));

        when(items.existsByCategoryAndNameIgnoreCase(meals, "Tea")).thenReturn(true);
        assertThrows(MenuException.class,
                () -> service.createItem("mgr", meals.getPublicId(), "Tea", "", "50"));

        MenuCategory closed = category("Closed");
        closed.setActive(false);
        assertThrows(MenuException.class,
                () -> service.createItem("mgr", closed.getPublicId(), "Soup", "", "100"));
        verify(items, never()).save(any());
    }

    @Test
    void priceChangeIsAuditedWithOldAndNewValue() {
        MenuCategory meals = category("Meals");
        MenuItem stew = item(meals, "Beef Stew", "300");
        service.updateItem("mgr", stew.getPublicId(), meals.getPublicId(), "Beef Stew", "desc", "320");
        assertEquals(new BigDecimal("320.00"), stew.getPrice());
        verify(audit).record(eq("mgr"), eq("MENU_PRICE_CHANGED"), eq("MENU_ITEM"), eq(stew.getPublicId()),
                eq("SUCCESS"), argThat(d -> d.contains("300.00 -> 320.00")));
        verify(audit, never()).record(any(), eq("MENU_ITEM_UPDATED"), any(), any(), any(), any());
    }

    @Test
    void renamingWithoutPriceChangeWritesNoPriceAudit() {
        MenuCategory meals = category("Meals");
        MenuItem stew = item(meals, "Beef Stew", "300");
        service.updateItem("mgr", stew.getPublicId(), meals.getPublicId(), "Beef Stew Special", "desc", "300");
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_UPDATED"), eq("MENU_ITEM"), eq(stew.getPublicId()),
                eq("SUCCESS"), anyString());
        verify(audit, never()).record(any(), eq("MENU_PRICE_CHANGED"), any(), any(), any(), any());
    }

    @Test
    void availabilityChangeIsAuditedAndRepeatIsIgnored() {
        MenuCategory drinks = category("Drinks");
        MenuItem juice = item(drinks, "Fresh Juice", "120");
        service.setItemAvailable("mgr", juice.getPublicId(), false);
        service.setItemAvailable("mgr", juice.getPublicId(), false);
        assertFalse(juice.isAvailable());
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_AVAILABILITY_CHANGED"), eq("MENU_ITEM"),
                eq(juice.getPublicId()), eq("SUCCESS"), argThat(d -> d.contains("available=false")));
    }

    @Test
    void unknownItemGivesSafeMessage() {
        when(items.findByPublicId("nope")).thenReturn(Optional.empty());
        MenuException e = assertThrows(MenuException.class, () -> service.setItemActive("mgr", "nope", false));
        assertEquals("That item was not found.", e.getMessage());
    }

    // ---- student view ----

    @Test
    void studentMenuHidesInactiveEntriesButKeepsSoldOutItemsMarked() {
        MenuCategory drinks = new MenuCategory("Drinks", null, 1);
        MenuCategory empty = new MenuCategory("Empty", null, 2);
        MenuItem tea = new MenuItem(drinks, "Tea", null, new BigDecimal("50.00"));
        MenuItem juice = new MenuItem(drinks, "Fresh Juice", null, new BigDecimal("120.00"));
        juice.setAvailable(false);
        MenuItem old = new MenuItem(drinks, "Old Soda", null, new BigDecimal("80.00"));
        old.setActive(false);

        when(categories.findByActiveTrueOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(drinks, empty));
        when(items.findByCategoryOrderByNameAsc(drinks)).thenReturn(List.of(juice, old, tea));
        when(items.findByCategoryOrderByNameAsc(empty)).thenReturn(List.of());

        List<CategoryView> sections = service.studentMenu();

        assertEquals(1, sections.size());
        List<ItemView> shown = sections.get(0).getItems();
        assertEquals(2, shown.size());
        assertEquals("Fresh Juice", shown.get(0).getName());
        assertFalse(shown.get(0).isAvailable());
        assertEquals("Tea", shown.get(1).getName());
        assertTrue(shown.get(1).isAvailable());
    }
}
