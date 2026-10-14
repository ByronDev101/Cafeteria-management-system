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
    private MenuImageStore imageStore;
    private AuditService audit;
    private MenuService service;

    @BeforeEach
    void setUp() {
        categories = mock(MenuCategoryRepository.class);
        items = mock(MenuItemRepository.class);
        imageStore = mock(MenuImageStore.class);
        audit = mock(AuditService.class);
        service = new MenuService(categories, items, imageStore, audit);
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

    private static ItemInput input(MenuCategory category, String name, String price) {
        return new ItemInput(category.getPublicId(), name, "desc", price, "", false, false);
    }

    private static byte[] goodPhoto() throws Exception {
        return ImageProcessorTest.encode(ImageProcessorTest.picture(120, 80), "png");
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

    // ---- creating items ----

    @Test
    void createItemStoresPriceWithTwoDecimalsAndDesignFields() {
        MenuCategory meals = category("Meals");
        ItemInput input = new ItemInput(meals.getPublicId(), "Chicken and Rice", "Roast chicken", "350", "12", true, true);
        service.createItem("mgr", input, null);
        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(items).save(saved.capture());
        MenuItem item = saved.getValue();
        assertEquals(new BigDecimal("350.00"), item.getPrice());
        assertEquals(12, item.getPrepMinutes());
        assertTrue(item.isVegetarian());
        assertTrue(item.isFeatured());
        assertTrue(item.isAvailable());
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_CREATED"), eq("MENU_ITEM"), eq(item.getPublicId()),
                eq("SUCCESS"), argThat(d -> d.contains("350.00")));
        verify(imageStore, never()).save(any(), any());
    }

    @Test
    void createItemWithPhotoStoresTheProcessedImageAndAudits() throws Exception {
        MenuCategory meals = category("Meals");
        service.createItem("mgr", input(meals, "Chicken and Rice", "350"), goodPhoto());
        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(items).save(saved.capture());
        verify(imageStore).save(eq(saved.getValue()), any(ImageProcessor.Processed.class));
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_IMAGE_CHANGED"), eq("MENU_ITEM"),
                eq(saved.getValue().getPublicId()), eq("SUCCESS"), anyString());
    }

    @Test
    void anUnusablePhotoStopsTheWholeSave() {
        MenuCategory meals = category("Meals");
        assertThrows(MenuException.class,
                () -> service.createItem("mgr", input(meals, "Chicken and Rice", "350"), "not an image".getBytes()));
        verify(items, never()).save(any());
        verify(imageStore, never()).save(any(), any());
    }

    @Test
    void createItemRejectsBadPriceDuplicateInactiveCategoryAndBadPrepTime() {
        MenuCategory meals = category("Meals");
        assertThrows(MenuException.class, () -> service.createItem("mgr", input(meals, "Stew", "-5"), null));
        assertThrows(MenuException.class, () -> service.createItem("mgr", input(meals, "Stew", "abc"), null));
        for (String prep : new String[] {"0", "181", "abc", "-3"}) {
            ItemInput badPrep = new ItemInput(meals.getPublicId(), "Stew", "", "100", prep, false, false);
            assertThrows(MenuException.class, () -> service.createItem("mgr", badPrep, null), prep);
        }

        when(items.existsByCategoryAndNameIgnoreCase(meals, "Tea")).thenReturn(true);
        assertThrows(MenuException.class, () -> service.createItem("mgr", input(meals, "Tea", "50"), null));

        MenuCategory closed = category("Closed");
        closed.setActive(false);
        assertThrows(MenuException.class, () -> service.createItem("mgr", input(closed, "Soup", "100"), null));
        verify(items, never()).save(any());
    }

    // ---- updating items ----

    @Test
    void priceChangeIsAuditedWithOldAndNewValue() {
        MenuCategory meals = category("Meals");
        MenuItem stew = item(meals, "Beef Stew", "300");
        ItemInput same = new ItemInput(meals.getPublicId(), "Beef Stew", "desc", "320", "", false, false);
        service.updateItem("mgr", stew.getPublicId(), same, null);
        assertEquals(new BigDecimal("320.00"), stew.getPrice());
        verify(audit).record(eq("mgr"), eq("MENU_PRICE_CHANGED"), eq("MENU_ITEM"), eq(stew.getPublicId()),
                eq("SUCCESS"), argThat(d -> d.contains("300.00 -> 320.00")));
        verify(audit, never()).record(any(), eq("MENU_ITEM_UPDATED"), any(), any(), any(), any());
    }

    @Test
    void renamingWithoutPriceChangeWritesNoPriceAudit() {
        MenuCategory meals = category("Meals");
        MenuItem stew = item(meals, "Beef Stew", "300");
        ItemInput renamed = new ItemInput(meals.getPublicId(), "Beef Stew Special", "desc", "300", "", false, false);
        service.updateItem("mgr", stew.getPublicId(), renamed, null);
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_UPDATED"), eq("MENU_ITEM"), eq(stew.getPublicId()),
                eq("SUCCESS"), anyString());
        verify(audit, never()).record(any(), eq("MENU_PRICE_CHANGED"), any(), any(), any(), any());
    }

    @Test
    void markingTodaysSpecialIsAnAuditedUpdate() {
        MenuCategory meals = category("Meals");
        MenuItem stew = item(meals, "Beef Stew", "300");
        ItemInput special = new ItemInput(meals.getPublicId(), "Beef Stew", "desc", "300", "10", false, true);
        service.updateItem("mgr", stew.getPublicId(), special, null);
        assertTrue(stew.isFeatured());
        assertEquals(10, stew.getPrepMinutes());
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_UPDATED"), eq("MENU_ITEM"), eq(stew.getPublicId()),
                eq("SUCCESS"), anyString());
    }

    @Test
    void replacingAPhotoIsAuditedAndABadReplacementKeepsTheOldOne() throws Exception {
        MenuCategory meals = category("Meals");
        MenuItem stew = item(meals, "Beef Stew", "300");
        ItemInput same = new ItemInput(meals.getPublicId(), "Beef Stew", "desc", "300", "", false, false);

        assertThrows(MenuException.class, () -> service.updateItem("mgr", stew.getPublicId(), same, "junk".getBytes()));
        verify(imageStore, never()).save(any(), any());

        service.updateItem("mgr", stew.getPublicId(), same, goodPhoto());
        verify(imageStore).save(eq(stew), any(ImageProcessor.Processed.class));
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_IMAGE_CHANGED"), eq("MENU_ITEM"), eq(stew.getPublicId()),
                eq("SUCCESS"), anyString());
    }

    @Test
    void removingAPhotoIsAuditedOnlyWhenThereIsOne() {
        MenuCategory meals = category("Meals");
        MenuItem withPhoto = item(meals, "Beef Stew", "300");
        withPhoto.setImageVersion(5L);
        MenuItem withoutPhoto = item(meals, "Tea", "50");

        service.removeItemImage("mgr", withPhoto.getPublicId());
        service.removeItemImage("mgr", withoutPhoto.getPublicId());

        verify(imageStore).remove(withPhoto);
        verify(imageStore, never()).remove(withoutPhoto);
        verify(audit).record(eq("mgr"), eq("MENU_ITEM_IMAGE_REMOVED"), eq("MENU_ITEM"),
                eq(withPhoto.getPublicId()), eq("SUCCESS"), anyString());
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

    @Test
    void heroShowsAtMostThreeAvailableFeaturedItems() {
        MenuCategory meals = new MenuCategory("Meals", null, 1);
        java.util.ArrayList<ItemView> all = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            all.add(new ItemView("i" + i, "Dish " + i, null, new BigDecimal("100.00"), i != 2, true,
                    meals.getPublicId(), "Meals", 10, false, true, null));
        }
        all.add(new ItemView("x", "Plain", null, new BigDecimal("100.00"), true, true,
                meals.getPublicId(), "Meals", 10, false, false, null));
        List<ItemView> hero = MenuService.featured(
                List.of(new CategoryView(meals.getPublicId(), "Meals", null, true, 1, all)));
        assertEquals(3, hero.size());
        assertEquals(List.of("i1", "i3", "i4"), hero.stream().map(ItemView::getPublicId).toList());
    }
}
