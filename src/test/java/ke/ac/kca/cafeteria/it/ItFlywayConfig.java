package ke.ac.kca.cafeteria.it;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wipes and rebuilds the schema from the migrations at the start of an integration-test run, so the tests prove the
 * migrations work from nothing. It refuses to touch any database whose name does not end in _test.
 */
@Configuration
@Profile("it")
class ItFlywayConfig {

    private static final Pattern TEST_DATABASE = Pattern.compile(".*/[A-Za-z0-9_]+_test(\\?.*)?$");

    @Bean
    FlywayMigrationStrategy cleanThenMigrate(@Value("${spring.datasource.url}") String url) {
        return flyway -> {
            if (!TEST_DATABASE.matcher(url).matches()) {
                throw new IllegalStateException(
                        "Refusing to wipe a database whose name does not end in _test. Check IT_DB_URL.");
            }
            flyway.clean();
            flyway.migrate();
        };
    }
}
