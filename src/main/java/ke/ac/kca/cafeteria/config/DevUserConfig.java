package ke.ac.kca.cafeteria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * TEMPORARY. Lets you prove the login flow in Sprint 0 with the "dev" profile only.
 * Delete this class in Sprint 1 when database-backed accounts replace it.
 */
@Configuration
@Profile("dev")
public class DevUserConfig {

    @Bean
    UserDetailsService devUsers(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername("dev.student")
                .password(encoder.encode("ChangeMe123!"))
                .roles("STUDENT")
                .build());
    }
}
