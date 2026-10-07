package ke.ac.kca.cafeteria.identity;

import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Counts wrong passwords and temporarily locks an account after repeated failures. */
@Component
public class LoginAttemptListener {

    private final UserRepository users;

    public LoginAttemptListener(UserRepository users) {
        this.users = users;
    }

    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        users.findByUsername(AppUser.normalize(event.getAuthentication().getName()))
                .ifPresent(user -> user.registerFailedLogin(Instant.now()));
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        users.findByUsername(AppUser.normalize(event.getAuthentication().getName()))
                .ifPresent(AppUser::registerSuccessfulLogin);
    }
}
