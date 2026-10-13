package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemViewTest {

    private ItemView item(String price, Integer prep, boolean vegetarian) {
        return new ItemView("i1", "Chips", "Fried potato chips", new BigDecimal(price), true, true,
                "c1", "Snacks", prep, vegetarian, false, null);
    }

    @Test
    void budgetFastAndVegetarianTagsAreDerived() {
        assertEquals(List.of("vegetarian", "budget", "fast"), item("150.00", 9, true).getTags());
        assertEquals(List.of("budget"), item("200.00", 12, false).getTags());
        assertEquals(List.of(), item("350.00", 12, false).getTags());
        assertEquals(List.of("fast"), item("350.00", 10, false).getTags());
    }

    @Test
    void missingPrepTimeIsNotFast() {
        ItemView view = item("350.00", null, false);
        assertFalse(view.isFast());
        assertNull(view.getPrepText());
    }

    @Test
    void prepTextAndPriceTextAreReadable() {
        ItemView view = item("1250.50", 12, false);
        assertEquals("12 min", view.getPrepText());
        assertEquals("KSh 1,250.50", view.getPriceText());
        assertEquals("1250.50", view.getPriceInput());
    }

    @Test
    void searchTextCoversNameDescriptionAndCategoryInLowerCase() {
        String text = item("150.00", 9, true).getSearchText();
        assertTrue(text.contains("chips"));
        assertTrue(text.contains("potato"));
        assertTrue(text.contains("snacks"));
        assertEquals(text, text.toLowerCase());
    }

    @Test
    void shortConstructorDefaultsTheDesignFields() {
        ItemView view = new ItemView("i1", "Tea", null, new BigDecimal("50.00"), true, true, "c1", "Drinks");
        assertFalse(view.isFeatured());
        assertFalse(view.isVegetarian());
        assertNull(view.getImageVersion());
        assertEquals("tea drinks", view.getSearchText());
    }
}
