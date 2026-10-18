package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CartModelAdviceTest {

    private final CartModelAdvice advice = new CartModelAdvice();

    private static UsernamePasswordAuthenticationToken signedIn(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("someone", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void studentsSeeTheNumberOfItemsInTheirCart() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cart cart = new Cart();
        cart.add("tea", 2);
        cart.add("samosa", 1);
        request.getSession().setAttribute(CartSession.ATTRIBUTE, cart);
        assertEquals(3, advice.cartCount(signedIn("ROLE_STUDENT"), request));
    }

    @Test
    void aStudentWithoutASessionOrCartSeesZeroAndNoSessionIsCreated() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(0, advice.cartCount(signedIn("ROLE_STUDENT"), request));
        assertNull(request.getSession(false));
    }

    @Test
    void otherRolesAndVisitorsGetNoCartIcon() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNull(advice.cartCount(signedIn("ROLE_MANAGER"), request));
        assertNull(advice.cartCount(signedIn("ROLE_ADMIN"), request));
        assertNull(advice.cartCount(null, request));
    }
}
