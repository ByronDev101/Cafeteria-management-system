package ke.ac.kca.cafeteria.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginThrottleFilterTest {

    private LoginThrottle throttle;
    private LoginThrottleFilter filter;

    @BeforeEach
    void setUp() {
        throttle = new LoginThrottle(new RateLimiter(2, Duration.ofMinutes(15)));
        filter = new LoginThrottleFilter(throttle);
    }

    private static MockHttpServletRequest request(String method, String path, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(address);
        return request;
    }

    private void block(String address) {
        throttle.onFailure(new org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent(
                detailed(address), new org.springframework.security.authentication.BadCredentialsException("bad")));
        throttle.onFailure(new org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent(
                detailed(address), new org.springframework.security.authentication.BadCredentialsException("bad")));
    }

    private static org.springframework.security.core.Authentication detailed(String address) {
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.unauthenticated("amina", "x");
        token.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetails(address, "s"));
        return token;
    }

    @Test
    void aSignInFromAnAddressThatIsNotBlockedGoesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/login", "10.0.0.5"), response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void aSignInFromABlockedAddressIsSentBackBeforeAnyPasswordIsChecked() throws Exception {
        block("10.0.0.5");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/login", "10.0.0.5"), response, chain);

        assertNull(chain.getRequest());
        assertEquals(302, response.getStatus());
        assertEquals("/login?throttled", response.getRedirectedUrl());
    }

    @Test
    void theSignInPageItselfAndOtherPagesAreNeverBlocked() throws Exception {
        block("10.0.0.5");
        for (String[] page : new String[][] {{"GET", "/login"}, {"GET", "/student/menu"}, {"POST", "/logout"},
                {"POST", "/student/cart/add"}}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request(page[0], page[1], "10.0.0.5"), response, chain);

            assertNotNull(chain.getRequest(), page[0] + " " + page[1]);
            assertEquals(200, response.getStatus(), page[0] + " " + page[1]);
        }
    }

    @Test
    void anotherAddressCanStillSignInWhileOneIsBlocked() throws Exception {
        block("10.0.0.5");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/login", "10.0.0.6"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void aForgedForwardingHeaderCannotDodgeTheBlock() throws Exception {
        block("10.0.0.5");
        MockHttpServletRequest request = request("POST", "/login", "10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.77");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals("/login?throttled", response.getRedirectedUrl());
    }

    @Test
    void theContextPathIsRespected() throws Exception {
        block("10.0.0.5");
        MockHttpServletRequest request = request("POST", "/cafe/login", "10.0.0.5");
        request.setContextPath("/cafe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("/cafe/login?throttled", response.getRedirectedUrl());
    }
}
