package ke.ac.kca.cafeteria.identity;

import java.security.SecureRandom;

/** Generates one-time passwords for new or reset accounts. Characters that look alike are left out. */
public final class TemporaryPassword {

    public static final int LENGTH = 12;

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPassword() {
    }

    public static String generate() {
        while (true) {
            StringBuilder sb = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
            }
            String candidate = sb.toString();
            if (containsAny(candidate, UPPER) && containsAny(candidate, LOWER) && containsAny(candidate, DIGITS)) {
                return candidate;
            }
        }
    }

    private static boolean containsAny(String text, String set) {
        return text.chars().anyMatch(ch -> set.indexOf(ch) >= 0);
    }
}
