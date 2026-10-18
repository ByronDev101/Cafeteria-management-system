package ke.ac.kca.cafeteria.menu;

import java.math.BigDecimal;

/**
 * An item as the cart and checkout see it, with today's authoritative price. {@code problem} is null when the item
 * can be ordered, otherwise a short reason such as "sold out" or "no longer on the menu".
 */
public record OrderableItem(Long id, String publicId, String name, BigDecimal unitPrice,
                            boolean orderable, String problem, Long imageVersion) {
}
