package ke.ac.kca.cafeteria.reporting;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A second lock behind the URL rules, like StaffGuard: the report service itself checks that the signed-in person is
 * a manager, so a mistake in a route can never show sales and payment figures to anyone else.
 */
public final class ManagerGuard {

    private ManagerGuard() {
    }

    public static void require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("Only managers can view reports.");
        }
    }
}
