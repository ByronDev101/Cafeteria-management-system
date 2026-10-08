package ke.ac.kca.cafeteria.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLongPasswordWithLetterAndDigit() {
        assertTrue(PasswordPolicy.check("river42lamps", "demo.student").isEmpty());
    }

    @Test
    void rejectsShortPassword() {
        assertFalse(PasswordPolicy.check("abc123", "demo.student").isEmpty());
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertFalse(PasswordPolicy.check("onlyletterslong", "demo.student").isEmpty());
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        assertFalse(PasswordPolicy.check("1234567890123", "demo.student").isEmpty());
    }

    @Test
    void rejectsPasswordEqualToUsername() {
        assertFalse(PasswordPolicy.check("Student12345", "student12345").isEmpty());
    }

    @Test
    void rejectsEmptyPassword() {
        assertFalse(PasswordPolicy.check("", "demo.student").isEmpty());
        assertFalse(PasswordPolicy.check(null, "demo.student").isEmpty());
    }
}
