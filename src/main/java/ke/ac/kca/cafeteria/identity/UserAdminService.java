package ke.ac.kca.cafeteria.identity;

import ke.ac.kca.cafeteria.reporting.AuditService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration (FR-003, FR-033). Every change and its audit record commit together.
 * The actor is always taken from the signed-in session by the controller, never from the request body.
 */
@Service
public class UserAdminService {

    public static final List<String> ROLE_NAMES = List.of("STUDENT", "STAFF", "MANAGER", "ADMIN");

    private static final int PAGE_SIZE = 20;
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._/-]{2,49}$");
    private static final String TARGET = "USER";
    private static final String SUCCESS = "SUCCESS";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public Page<UserRow> list(int page, String actorUsername) {
        Instant now = Instant.now();
        return users.findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("username")))
                .map(user -> UserRow.from(user, actorUsername, now));
    }

    @Transactional(readOnly = true)
    public UserRow details(String publicId, String actorUsername) {
        return UserRow.from(find(publicId), actorUsername, Instant.now());
    }

    // ---- creating ----

    @Transactional
    public CreatedAccount createUser(String actor, String rawUsername, String displayName,
                                     Collection<String> roleNames) {
        String username = AppUser.normalize(rawUsername);
        String name = displayName == null ? "" : displayName.trim();

        List<String> problems = new ArrayList<>();
        if (!USERNAME.matcher(username).matches()) {
            problems.add("The username must be 3 to 50 characters: letters, digits and . _ / - only.");
        }
        if (name.length() < 2 || name.length() > 100) {
            problems.add("The display name must be 2 to 100 characters.");
        }
        Set<Role> chosen = resolveRoles(roleNames, problems);
        if (problems.isEmpty() && users.existsByUsername(username)) {
            problems.add("That username is already in use.");
        }
        if (!problems.isEmpty()) {
            throw new UserAdminException(problems);
        }

        String temporary = TemporaryPassword.generate();
        AppUser user = new AppUser(username, encoder.encode(temporary), name);
        user.requirePasswordChange();
        chosen.forEach(user::addRole);
        users.save(user);

        audit.record(actor, AuditService.USER_CREATED, TARGET, user.getPublicId(), SUCCESS,
                "username=" + username + "; roles=" + String.join(",", user.roleNames()));
        return new CreatedAccount(user.getPublicId(), username, temporary);
    }

    // ---- changing ----

    @Transactional
    public void setActive(String actor, String publicId, boolean active) {
        AppUser target = find(publicId);
        if (target.isActive() == active) {
            return;
        }
        if (!active) {
            if (target.getUsername().equals(actor)) {
                throw new UserAdminException("You can't deactivate your own account.");
            }
            if (target.hasRole("ADMIN") && users.countActiveWithRole("ADMIN") <= 1) {
                throw new UserAdminException("At least one active administrator must remain.");
            }
        }
        target.setActive(active);
        audit.record(actor, active ? AuditService.USER_ACTIVATED : AuditService.USER_DEACTIVATED,
                TARGET, target.getPublicId(), SUCCESS, "username=" + target.getUsername());
    }

    @Transactional
    public void setRoles(String actor, String publicId, Collection<String> roleNames) {
        AppUser target = find(publicId);

        List<String> problems = new ArrayList<>();
        Set<Role> newRoles = resolveRoles(roleNames, problems);
        if (!problems.isEmpty()) {
            throw new UserAdminException(problems);
        }

        boolean keepsAdmin = newRoles.stream().anyMatch(role -> "ADMIN".equals(role.getName()));
        if (target.hasRole("ADMIN") && !keepsAdmin) {
            if (target.getUsername().equals(actor)) {
                throw new UserAdminException("You can't remove your own administrator role.");
            }
            if (target.isActive() && users.countActiveWithRole("ADMIN") <= 1) {
                throw new UserAdminException("At least one active administrator must remain.");
            }
        }

        String before = String.join(",", target.roleNames());
        target.replaceRoles(newRoles);
        String after = String.join(",", target.roleNames());
        if (!before.equals(after)) {
            audit.record(actor, AuditService.USER_ROLES_CHANGED, TARGET, target.getPublicId(), SUCCESS,
                    "username=" + target.getUsername() + "; roles: " + before + " -> " + after);
        }
    }

    @Transactional
    public void unlock(String actor, String publicId) {
        AppUser target = find(publicId);
        target.unlock();
        audit.record(actor, AuditService.USER_UNLOCKED, TARGET, target.getPublicId(), SUCCESS,
                "username=" + target.getUsername());
    }

    @Transactional
    public CreatedAccount resetPassword(String actor, String publicId) {
        AppUser target = find(publicId);
        if (target.getUsername().equals(actor)) {
            throw new UserAdminException("Use the Change password page for your own account.");
        }
        String temporary = TemporaryPassword.generate();
        target.changePassword(encoder.encode(temporary), true);
        audit.record(actor, AuditService.PASSWORD_RESET, TARGET, target.getPublicId(), SUCCESS,
                "username=" + target.getUsername());
        return new CreatedAccount(target.getPublicId(), target.getUsername(), temporary);
    }

    // ---- own password ----

    /**
     * Wrong current passwords count towards the sign-in lockout, and that count is kept
     * even though the method then throws (hence noRollbackFor).
     */
    @Transactional(noRollbackFor = UserAdminException.class)
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        AppUser user = users.findByUsername(AppUser.normalize(username))
                .orElseThrow(() -> new UserAdminException("Your account was not found."));

        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            user.registerFailedLogin(Instant.now());
            audit.record(user.getUsername(), AuditService.PASSWORD_CHANGE_FAILED, TARGET, user.getPublicId(),
                    "FAILURE", "current password was wrong");
            throw new UserAdminException("The current password is not correct.");
        }

        List<String> problems = new ArrayList<>(PasswordPolicy.check(newPassword, user.getUsername()));
        if (problems.isEmpty() && newPassword.equals(currentPassword)) {
            problems.add("Choose a password that is different from your current one.");
        }
        if (!problems.isEmpty()) {
            throw new UserAdminException(problems);
        }

        user.changePassword(encoder.encode(newPassword), false);
        audit.record(user.getUsername(), AuditService.PASSWORD_CHANGED, TARGET, user.getPublicId(), SUCCESS, null);
    }

    // ---- helpers ----

    private AppUser find(String publicId) {
        return users.findByPublicId(publicId)
                .orElseThrow(() -> new UserAdminException("That account was not found."));
    }

    private Set<Role> resolveRoles(Collection<String> names, List<String> problems) {
        Set<Role> result = new LinkedHashSet<>();
        if (names != null) {
            for (String raw : names) {
                String name = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
                if (!ROLE_NAMES.contains(name)) {
                    problems.add("Unknown role.");
                    continue;
                }
                roles.findByName(name).ifPresent(result::add);
            }
        }
        if (result.isEmpty() && problems.isEmpty()) {
            problems.add("Choose at least one role.");
        }
        return result;
    }
}
