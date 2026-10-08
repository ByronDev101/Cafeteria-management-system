package ke.ac.kca.cafeteria.identity;

import java.util.ArrayList;
import java.util.List;

/** Password rules for passwords people choose themselves (NFR-02). Temporary passwords are exempt. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;     // BCrypt only uses the first 72 bytes

    private PasswordPolicy() {
    }

    /** Returns a list of plain-language problems; empty means the password is acceptable. */
    public static List<String> check(String candidate, String username) {
        List<String> problems = new ArrayList<>();
        if (candidate == null || candidate.isEmpty()) {
            problems.add("Enter a new password.");
            return problems;
        }
        if (candidate.length() < MIN_LENGTH) {
            problems.add("The password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (candidate.length() > MAX_LENGTH) {
            problems.add("The password must be at most " + MAX_LENGTH + " characters long.");
        }
        boolean hasLetter = candidate.chars().anyMatch(Character::isLetter);
        boolean hasDigit = candidate.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            problems.add("The password must include at least one letter and one digit.");
        }
        if (username != null && candidate.equalsIgnoreCase(username)) {
            problems.add("The password must not be the same as your username.");
        }
        return problems;
    }
}
