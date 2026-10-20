package ke.ac.kca.cafeteria.ordering;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A second lock behind the URL rules (SDS 7): the processing service itself checks that the signed-in person is
 * staff or a manager, so a mistake in a route can never let a student or an administrator change an order.
 */
public final class StaffGuard {

    private StaffGuard() {
    }

    public static void require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_MANAGER".equals(a.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("Only staff or managers can process orders.");
        }
    }
}
