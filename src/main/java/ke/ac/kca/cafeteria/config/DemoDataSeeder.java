package ke.ac.kca.cafeteria.config;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.identity.RoleRepository;
import ke.ac.kca.cafeteria.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates fictional demo accounts, one per role, in the "dev" profile only (NFR-04: synthetic data).
 * Never runs in any other profile. The password comes from app.demo.password.
 */
@Component
@Profile("dev")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                          @Value("${app.demo.password:ChangeMe123!}") String demoPassword) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seed("demo.student", "Demo Student", "STUDENT");
        seed("demo.staff", "Demo Staff", "STAFF");
        seed("demo.manager", "Demo Manager", "MANAGER");
        seed("demo.admin", "Demo Administrator", "ADMIN");
    }

    private void seed(String username, String displayName, String roleName) {
        if (users.existsByUsername(username)) {
            return;
        }
        AppUser user = new AppUser(username, encoder.encode(demoPassword), displayName);
        user.addRole(roles.findByName(roleName).orElseThrow());
        users.save(user);
        log.info("Seeded demo account {} with role {} (dev profile only)", username, roleName);
    }
}
