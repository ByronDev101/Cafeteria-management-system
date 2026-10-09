package ke.ac.kca.cafeteria.identity;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads accounts from MySQL for Spring Security (FR-001).
 * Unknown users surface as a generic bad-credentials failure, so the login page
 * never reveals whether an account exists.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByUsername(AppUser.normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toList();

        return new AppUserPrincipal(
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                !user.isLockedAt(Instant.now()),
                user.isMustChangePassword(),
                authorities);
    }
}
