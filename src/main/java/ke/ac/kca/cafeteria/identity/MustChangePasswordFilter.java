package ke.ac.kca.cafeteria.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps users with a temporary password on the change-password page until they set their own.
 * Created with "new" inside SecurityConfig on purpose (not a bean), so it runs only in the security chain.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    static final String TARGET = "/account/password";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isMustChangePassword()
                && !isAllowed(request)) {
            response.sendRedirect(request.getContextPath() + TARGET);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(TARGET) || path.equals("/logout") || path.equals("/error")
                || path.startsWith("/webjars/") || path.startsWith("/css/") || path.startsWith("/js/");
    }
}
