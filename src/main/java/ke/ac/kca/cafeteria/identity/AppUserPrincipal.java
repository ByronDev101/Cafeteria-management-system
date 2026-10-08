package ke.ac.kca.cafeteria.identity;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security user that also remembers whether a password change is required. */
public class AppUserPrincipal extends User {

    private final boolean mustChangePassword;

    public AppUserPrincipal(String username, String password, boolean enabled, boolean accountNonLocked,
                            boolean mustChangePassword, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
