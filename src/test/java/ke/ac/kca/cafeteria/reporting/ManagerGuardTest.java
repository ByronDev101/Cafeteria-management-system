package ke.ac.kca.cafeteria.reporting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ManagerGuardTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("pat", null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void aManagerPasses() {
        signInAs("ROLE_MANAGER");
        assertDoesNotThrow(ManagerGuard::require);
    }

    @Test
    void everyoneElseIsTurnedAway() {
        for (String role : List.of("ROLE_STAFF", "ROLE_STUDENT", "ROLE_ADMIN")) {
            signInAs(role);
            assertThrows(AccessDeniedException.class, ManagerGuard::require, role);
        }
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, ManagerGuard::require);
    }
}
