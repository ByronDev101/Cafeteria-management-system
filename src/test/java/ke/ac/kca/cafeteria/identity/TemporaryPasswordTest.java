package ke.ac.kca.cafeteria.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TemporaryPasswordTest {

    @Test
    void hasExpectedLengthAndMixedCharacters() {
        for (int i = 0; i < 50; i++) {
            String password = TemporaryPassword.generate();
            assertEquals(TemporaryPassword.LENGTH, password.length());
            assertTrue(password.chars().anyMatch(Character::isUpperCase));
            assertTrue(password.chars().anyMatch(Character::isLowerCase));
            assertTrue(password.chars().anyMatch(Character::isDigit));
        }
    }

    @Test
    void generatesDifferentValues() {
        assertNotEquals(TemporaryPassword.generate(), TemporaryPassword.generate());
    }
}
