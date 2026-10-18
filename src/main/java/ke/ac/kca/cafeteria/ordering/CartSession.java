package ke.ac.kca.cafeteria.ordering;

import jakarta.servlet.http.HttpSession;

/** Reads and writes the cart in the student's session. Signing out ends the session and empties the cart. */
public final class CartSession {

    public static final String ATTRIBUTE = "cart";

    private CartSession() {
    }

    /** The cart, created on first use. */
    public static Cart get(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (value instanceof Cart cart) {
            return cart;
        }
        Cart cart = new Cart();
        session.setAttribute(ATTRIBUTE, cart);
        return cart;
    }

    /** The cart if one exists, otherwise null. Never creates a session or a cart. */
    public static Cart peek(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(ATTRIBUTE);
        return value instanceof Cart cart ? cart : null;
    }
}
