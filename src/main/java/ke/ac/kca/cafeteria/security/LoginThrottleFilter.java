package ke.ac.kca.cafeteria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns away sign-in attempts (POST /login) from an address that has failed too often recently, before any password
 * is checked, and sends the person back to the sign-in page with a short explanation. Only the address in the
 * connection is used. Forwarding headers are never trusted here: behind a proxy, the server is configured to set the
 * real address itself.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleFilter.class);

    private final LoginThrottle throttle;

    public LoginThrottleFilter(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getRemoteAddr();
        if (throttle.isBlocked(client)) {
            log.warn("Sign-in refused for {} after repeated failures; try again in about {} s", client,
                    throttle.retryAfter(client).toSeconds());
            response.sendRedirect(request.getContextPath() + "/login?throttled");
            return;
        }
        chain.doFilter(request, response);
    }
}
