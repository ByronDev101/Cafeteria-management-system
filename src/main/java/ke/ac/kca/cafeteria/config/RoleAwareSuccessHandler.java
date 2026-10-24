package ke.ac.kca.cafeteria.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Decides where a person goes right after signing in. If they were on their way to a page they are allowed to open
 * (for example a table QR link, or a page they used before their session ended), they are taken there. If the page they
 * had asked for belongs to another role, they are sent to their own home page instead of an "access denied" page.
 */
public class RoleAwareSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache;

    public RoleAwareSuccessHandler(RequestCache requestCache) {
        this.requestCache = requestCache;
        setRequestCache(requestCache);
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && mayVisit(saved.getRedirectUrl(), authentication)) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        requestCache.removeRequest(request, response);
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, "/");
    }

    /** True when the signed-in person's role may open the page at this address. */
    static boolean mayVisit(String url, Authentication authentication) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (path == null) {
            return false;
        }
        if (path.startsWith("/t/") || path.startsWith("/student/")) {
            return has(authentication, "ROLE_STUDENT");
        }
        if (path.startsWith("/staff/")) {
            return has(authentication, "ROLE_STAFF") || has(authentication, "ROLE_MANAGER");
        }
        if (path.startsWith("/manager/")) {
            return has(authentication, "ROLE_MANAGER");
        }
        if (path.startsWith("/admin/")) {
            return has(authentication, "ROLE_ADMIN");
        }
        return path.startsWith("/account/");
    }

    private static boolean has(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }
}
