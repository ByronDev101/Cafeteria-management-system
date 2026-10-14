package ke.ac.kca.cafeteria.identity;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security user that also remembers the display name and whether a password change is required. */
public class AppUserPrincipal extends User {

    private final boolean mustChangePassword;
    private final String displayName;

    public AppUserPrincipal(String username, String password, boolean enabled, boolean accountNonLocked,
                            boolean mustChangePassword, Collection<? extends GrantedAuthority> authorities) {
        this(username, username, password, enabled, accountNonLocked, mustChangePassword, authorities);
    }

    public AppUserPrincipal(String username, String displayName, String password, boolean enabled,
                            boolean accountNonLocked, boolean mustChangePassword,
                            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.displayName = displayName;
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getDisplayName() {
        return displayName;
    }
}
