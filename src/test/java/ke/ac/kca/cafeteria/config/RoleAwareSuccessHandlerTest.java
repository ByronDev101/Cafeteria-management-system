package ke.ac.kca.cafeteria.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class RoleAwareSuccessHandlerTest {

    private final HttpSessionRequestCache cache = new HttpSessionRequestCache();
    private final RoleAwareSuccessHandler handler = new RoleAwareSuccessHandler(cache);

    RoleAwareSuccessHandlerTest() {
        cache.setMatchingRequestParameterName(null);
    }

    private static Authentication signedInAs(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of(new SimpleGrantedAuthority(role)));
    }

    /** Someone asks for a page while signed out, is sent to sign in, signs in; returns where they are sent next. */
    private String signInAfterAskingFor(String page, String role) throws Exception {
        MockHttpServletRequest asked = new MockHttpServletRequest("GET", page);
        cache.saveRequest(asked, new MockHttpServletResponse());

        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        login.setSession(asked.getSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(login, response, signedInAs(role));
        return response.getRedirectedUrl();
    }

    @Test
    void aStudentWhoScannedAQrCodeContinuesToTheTable() throws Exception {
        assertEquals("http://localhost/t/ABCDEFGHJKMNPQRSTVWXYZ0123",
                signInAfterAskingFor("/t/ABCDEFGHJKMNPQRSTVWXYZ0123", "ROLE_STUDENT"));
    }

    @Test
    void eachRoleContinuesToItsOwnPages() throws Exception {
        assertEquals("http://localhost/student/menu", signInAfterAskingFor("/student/menu", "ROLE_STUDENT"));
        assertEquals("http://localhost/staff/orders", signInAfterAskingFor("/staff/orders", "ROLE_STAFF"));
        assertEquals("http://localhost/staff/orders", signInAfterAskingFor("/staff/orders", "ROLE_MANAGER"));
        assertEquals("http://localhost/manager/menu", signInAfterAskingFor("/manager/menu", "ROLE_MANAGER"));
        assertEquals("http://localhost/admin/users", signInAfterAskingFor("/admin/users", "ROLE_ADMIN"));
        assertEquals("http://localhost/account/password", signInAfterAskingFor("/account/password", "ROLE_STUDENT"));
    }

    @Test
    void aPageForAnotherRoleIsNeverVisitedAfterSignInSoNoAccessDeniedPageAppears() throws Exception {
        assertEquals("/", signInAfterAskingFor("/staff/orders", "ROLE_STUDENT"));
        assertEquals("/", signInAfterAskingFor("/admin/users", "ROLE_STUDENT"));
        assertEquals("/", signInAfterAskingFor("/manager/menu", "ROLE_STAFF"));
        assertEquals("/", signInAfterAskingFor("/t/ABCDEFGHJKMNPQRSTVWXYZ0123", "ROLE_MANAGER"));
        assertEquals("/", signInAfterAskingFor("/staff/orders", "ROLE_ADMIN"));
    }

    @Test
    void unknownPagesAndOrdinarySignInGoToTheStartPage() throws Exception {
        assertEquals("/", signInAfterAskingFor("/something-else", "ROLE_STUDENT"));

        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(login, response, signedInAs("ROLE_STUDENT"));
        assertEquals("/", response.getRedirectedUrl());
    }

    @Test
    void theRememberedPageIsForgottenAfterItIsUsedOrRefused() throws Exception {
        MockHttpServletRequest asked = new MockHttpServletRequest("GET", "/admin/users");
        cache.saveRequest(asked, new MockHttpServletResponse());
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        login.setSession(asked.getSession());
        handler.onAuthenticationSuccess(login, new MockHttpServletResponse(), signedInAs("ROLE_STUDENT"));
        assertNull(cache.getRequest(login, new MockHttpServletResponse()));
    }

    @Test
    void theRolePermissionRuleMatchesTheUrlRules() {
        Authentication student = signedInAs("ROLE_STUDENT");
        assertTrue(RoleAwareSuccessHandler.mayVisit("http://localhost/student/orders/abc", student));
        assertFalse(RoleAwareSuccessHandler.mayVisit("http://localhost/staff/orders", student));
        assertFalse(RoleAwareSuccessHandler.mayVisit("not a url at all ::", student));
        assertFalse(RoleAwareSuccessHandler.mayVisit("http://localhost/", student));
    }
}
