package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CartTest {

    @Test
    void addingTheSameItemMergesQuantities() {
        Cart cart = new Cart();
        cart.add("tea", 2);
        assertEquals(5, cart.add("tea", 3));
        assertEquals(5, cart.unitCount());
    }

    @Test
    void quantityIsCappedAtTenPerItemAndAFailedAddChangesNothing() {
        Cart cart = new Cart();
        cart.add("tea", 5);
        assertThrows(OrderException.class, () -> cart.add("tea", 6));
        assertEquals(5, cart.lines().get("tea"));
        assertThrows(OrderException.class, () -> cart.add("tea", 0));
        assertThrows(OrderException.class, () -> cart.add("tea", -2));
    }

    @Test
    void setQuantityChangesRemovesAndIgnoresUnknownItems() {
        Cart cart = new Cart();
        cart.add("tea", 2);
        cart.setQuantity("tea", 7);
        assertEquals(7, cart.lines().get("tea"));
        assertThrows(OrderException.class, () -> cart.setQuantity("tea", 11));
        cart.setQuantity("ghost", 3);
        assertFalse(cart.lines().containsKey("ghost"));
        cart.setQuantity("tea", 0);
        assertTrue(cart.isEmpty());
    }

    @Test
    void removeTakesOutTheWholeLine() {
        Cart cart = new Cart();
        cart.add("tea", 2);
        cart.remove("tea");
        assertTrue(cart.isEmpty());
    }

    @Test
    void aCartHoldsAtMostTwentyDifferentItems() {
        Cart cart = new Cart();
        for (int i = 0; i < Cart.MAX_LINES; i++) {
            cart.add("item" + i, 1);
        }
        assertThrows(OrderException.class, () -> cart.add("one-too-many", 1));
        cart.add("item0", 1);
        assertEquals(2, cart.lines().get("item0"));
    }

    @Test
    void linesReturnsACopy() {
        Cart cart = new Cart();
        cart.add("tea", 1);
        Map<String, Integer> copy = cart.lines();
        copy.put("hack", 99);
        assertFalse(cart.lines().containsKey("hack"));
    }

    @Test
    void clearingStartsAFreshDuplicateSubmitKey() {
        Cart cart = new Cart();
        cart.add("tea", 1);
        String before = cart.idempotencyKey();
        cart.clear();
        assertTrue(cart.isEmpty());
        assertNotEquals(before, cart.idempotencyKey());
    }
}
