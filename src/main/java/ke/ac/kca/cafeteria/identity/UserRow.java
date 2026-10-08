package ke.ac.kca.cafeteria.identity;

import java.time.Instant;
import java.util.List;

/** What the account list and role pages need to show about one account. */
public final class UserRow {

    private final String publicId;
    private final String username;
    private final String displayName;
    private final List<String> roleList;
    private final boolean active;
    private final boolean locked;
    private final boolean mustChangePassword;
    private final boolean self;

    private UserRow(AppUser user, String actorUsername, Instant now) {
        this.publicId = user.getPublicId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.roleList = user.roleNames();
        this.active = user.isActive();
        this.locked = user.isLockedAt(now);
        this.mustChangePassword = user.isMustChangePassword();
        this.self = user.getUsername().equals(actorUsername);
    }

    static UserRow from(AppUser user, String actorUsername, Instant now) {
        return new UserRow(user, actorUsername, now);
    }

    public String getPublicId() {
        return publicId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getRoleList() {
        return roleList;
    }

    public String getRoles() {
        return String.join(", ", roleList);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isSelf() {
        return self;
    }
}
