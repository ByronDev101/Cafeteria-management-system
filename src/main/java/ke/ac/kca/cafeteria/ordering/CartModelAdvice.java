package ke.ac.kca.cafeteria.ordering;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every page the number of items in a student's cart, for the badge in the top bar.
 * Other roles get nothing, so the cart icon never appears for them. It never creates a session.
 */
@ControllerAdvice
public class CartModelAdvice {

    @ModelAttribute("cartCount")
    public Integer cartCount(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()))) {
            return null;
        }
        Cart cart = CartSession.peek(request.getSession(false));
        return cart == null ? 0 : cart.unitCount();
    }
}
