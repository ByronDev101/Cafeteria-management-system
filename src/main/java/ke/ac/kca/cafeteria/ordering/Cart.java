package ke.ac.kca.cafeteria.ordering;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A student's cart (FR-012). It remembers only which items and how many, never prices: prices are looked up from
 * the menu every time the cart is shown or an order is placed (FR-013). It lives in the session.
 */
public final class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MAX_QUANTITY = 10;
    public static final int MAX_LINES = 20;

    private final LinkedHashMap<String, Integer> lines = new LinkedHashMap<>();
    private String idempotencyKey = UUID.randomUUID().toString();

    /** Adds to what is already in the cart. Returns the new quantity of that item. */
    public synchronized int add(String itemId, int quantity) {
        if (quantity < 1) {
            throw new OrderException("Choose at least one.");
        }
        int existing = lines.getOrDefault(itemId, 0);
        if (existing == 0 && lines.size() >= MAX_LINES) {
            throw new OrderException("Your cart can hold at most " + MAX_LINES + " different items.");
        }
        int total = existing + quantity;
        if (total > MAX_QUANTITY) {
            throw new OrderException("You can order at most " + MAX_QUANTITY + " of one item.");
        }
        lines.put(itemId, total);
        return total;
    }

    /** Sets an exact quantity. Zero or less removes the item. Items not in the cart are ignored. */
    public synchronized void setQuantity(String itemId, int quantity) {
        if (!lines.containsKey(itemId)) {
            return;
        }
        if (quantity <= 0) {
            lines.remove(itemId);
            return;
        }
        if (quantity > MAX_QUANTITY) {
            throw new OrderException("You can order at most " + MAX_QUANTITY + " of one item.");
        }
        lines.put(itemId, quantity);
    }

    public synchronized void remove(String itemId) {
        lines.remove(itemId);
    }

    /** Empties the cart and starts a new duplicate-submit key, ready for the next order. */
    public synchronized void clear() {
        lines.clear();
        idempotencyKey = UUID.randomUUID().toString();
    }

    public synchronized boolean isEmpty() {
        return lines.isEmpty();
    }

    public synchronized int unitCount() {
        return lines.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** A copy, safe to read while the session cart changes. */
    public synchronized Map<String, Integer> lines() {
        return new LinkedHashMap<>(lines);
    }

    public synchronized String idempotencyKey() {
        return idempotencyKey;
    }
}
