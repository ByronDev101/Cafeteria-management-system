package ke.ac.kca.cafeteria.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.reporting.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAdminServiceTest {

    private UserRepository users;
    private RoleRepository roles;
    private AuditService audit;
    private PasswordEncoder encoder;
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        audit = mock(AuditService.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new UserAdminService(users, roles, encoder, audit);
        for (String name : UserAdminService.ROLE_NAMES) {
            when(roles.findByName(name)).thenReturn(Optional.of(new Role(name)));
        }
    }

    private AppUser account(String username, String roleName) {
        AppUser user = new AppUser(username, encoder.encode("OldPassw0rd!"), username);
        user.addRole(new Role(roleName));
        when(users.findByPublicId(user.getPublicId())).thenReturn(Optional.of(user));
        when(users.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        return user;
    }

    // ---- creating ----

    @Test
    void createUserStoresHashedTemporaryPasswordAndAudits() {
        CreatedAccount created = service.createUser("demo.admin", " New.Student ", "New Student", List.of("STUDENT"));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        AppUser user = saved.getValue();

        assertEquals("new.student", user.getUsername());
        assertTrue(user.isMustChangePassword());
        assertNotEquals(created.temporaryPassword(), user.getPasswordHash());
        assertTrue(encoder.matches(created.temporaryPassword(), user.getPasswordHash()));
        verify(audit).record(eq("demo.admin"), eq("USER_CREATED"), eq("USER"), eq(user.getPublicId()),
                eq("SUCCESS"), argThat(details -> !details.contains(created.temporaryPassword())));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(users.existsByUsername("taken.user")).thenReturn(true);
        assertThrows(UserAdminException.class,
                () -> service.createUser("demo.admin", "taken.user", "Taken User", List.of("STUDENT")));
        verify(users, never()).save(any());
    }

    @Test
    void createUserRejectsBadUsernameAndMissingRole() {
        assertThrows(UserAdminException.class,
                () -> service.createUser("demo.admin", "a b", "Someone", List.of("STUDENT")));
        assertThrows(UserAdminException.class,
                () -> service.createUser("demo.admin", "good.name", "Someone", List.of()));
        assertThrows(UserAdminException.class,
                () -> service.createUser("demo.admin", "good.name", "Someone", List.of("SUPERUSER")));
        verify(users, never()).save(any());
    }

    // ---- activation guards ----

    @Test
    void adminCannotDeactivateOwnAccount() {
        AppUser admin = account("demo.admin", "ADMIN");
        assertThrows(UserAdminException.class, () -> service.setActive("demo.admin", admin.getPublicId(), false));
        assertTrue(admin.isActive());
    }

    @Test
    void lastActiveAdministratorCannotBeDeactivated() {
        AppUser admin = account("other.admin", "ADMIN");
        when(users.countActiveWithRole("ADMIN")).thenReturn(1L);
        assertThrows(UserAdminException.class, () -> service.setActive("demo.admin", admin.getPublicId(), false));
        assertTrue(admin.isActive());
    }

    @Test
    void studentCanBeDeactivatedAndChangeIsAudited() {
        AppUser student = account("demo.student", "STUDENT");
        service.setActive("demo.admin", student.getPublicId(), false);
        assertFalse(student.isActive());
        verify(audit).record(eq("demo.admin"), eq("USER_DEACTIVATED"), eq("USER"), eq(student.getPublicId()),
                eq("SUCCESS"), anyString());
    }

    // ---- roles ----

    @Test
    void adminCannotRemoveOwnAdministratorRole() {
        AppUser admin = account("demo.admin", "ADMIN");
        assertThrows(UserAdminException.class,
                () -> service.setRoles("demo.admin", admin.getPublicId(), List.of("STAFF")));
        assertTrue(admin.hasRole("ADMIN"));
    }

    @Test
    void roleChangeIsAudited() {
        AppUser student = account("demo.student", "STUDENT");
        service.setRoles("demo.admin", student.getPublicId(), List.of("STAFF"));
        assertTrue(student.hasRole("STAFF"));
        assertFalse(student.hasRole("STUDENT"));
        verify(audit).record(eq("demo.admin"), eq("USER_ROLES_CHANGED"), eq("USER"), eq(student.getPublicId()),
                eq("SUCCESS"), argThat(details -> details.contains("STUDENT -> STAFF")));
    }

    // ---- unlock and reset ----

    @Test
    void unlockClearsTheLock() {
        AppUser staff = account("demo.staff", "STAFF");
        for (int i = 0; i < AppUser.MAX_FAILED_ATTEMPTS; i++) {
            staff.registerFailedLogin(Instant.now());
        }
        assertTrue(staff.isLockedAt(Instant.now()));
        service.unlock("demo.admin", staff.getPublicId());
        assertFalse(staff.isLockedAt(Instant.now()));
    }

    @Test
    void resetPasswordSetsTemporaryPasswordThatMustBeChanged() {
        AppUser student = account("demo.student", "STUDENT");
        CreatedAccount reset = service.resetPassword("demo.admin", student.getPublicId());
        assertTrue(student.isMustChangePassword());
        assertTrue(encoder.matches(reset.temporaryPassword(), student.getPasswordHash()));
    }

    @Test
    void adminCannotResetOwnPasswordThroughAdminScreen() {
        AppUser admin = account("demo.admin", "ADMIN");
        assertThrows(UserAdminException.class, () -> service.resetPassword("demo.admin", admin.getPublicId()));
    }

    // ---- own password ----

    @Test
    void wrongCurrentPasswordIsRefusedAndCountsAsFailedAttempt() {
        AppUser student = account("demo.student", "STUDENT");
        assertThrows(UserAdminException.class,
                () -> service.changeOwnPassword("demo.student", "WrongPassw0rd", "BrandNewPass123"));
        assertEquals(1, student.getFailedAttempts());
        assertTrue(encoder.matches("OldPassw0rd!", student.getPasswordHash()));
    }

    @Test
    void weakNewPasswordIsRefused() {
        account("demo.student", "STUDENT");
        assertThrows(UserAdminException.class,
                () -> service.changeOwnPassword("demo.student", "OldPassw0rd!", "short1"));
    }

    @Test
    void validChangeStoresNewHashAndClearsMustChangeFlag() {
        AppUser student = account("demo.student", "STUDENT");
        student.requirePasswordChange();
        service.changeOwnPassword("demo.student", "OldPassw0rd!", "BrandNewPass123");
        assertFalse(student.isMustChangePassword());
        assertTrue(encoder.matches("BrandNewPass123", student.getPasswordHash()));
        verify(audit).record(eq("demo.student"), eq("PASSWORD_CHANGED"), eq("USER"), eq(student.getPublicId()),
                eq("SUCCESS"), eq(null));
    }
}
