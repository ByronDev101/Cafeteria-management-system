package ke.ac.kca.cafeteria.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class MustChangePasswordFilterTest {

    private final MustChangePasswordFilter filter = new MustChangePasswordFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(boolean mustChange) {
        AppUserPrincipal principal = new AppUserPrincipal("demo.student", "x", true, true, mustChange,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void userWithTemporaryPasswordIsSentToChangePage() throws Exception {
        signIn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/student/home"), response, chain);
        assertEquals("/account/password", response.getRedirectedUrl());
        assertNull(chain.getRequest());
    }

    @Test
    void changePageAndLogoutStayReachable() throws Exception {
        signIn(true);
        for (String path : List.of("/account/password", "/logout", "/css/app.css")) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest(), path);
        }
    }

    @Test
    void normalUserIsNotRedirected() throws Exception {
        signIn(false);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/student/home"), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }
}
