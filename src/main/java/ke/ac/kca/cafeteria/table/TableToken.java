package ke.ac.kca.cafeteria.table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Table QR tokens (FR-010, FR-011, SDS 6.5).
 * A token is 26 random characters from a 32-character alphabet, which is 130 bits from a cryptographically
 * secure generator. The alphabet leaves out I, L, O and U, so a code typed from a printed label is hard to
 * misread, and it is URL-safe. Only the SHA-256 hash of a token is ever stored.
 */
public final class TableToken {

    public static final int LENGTH = 26;

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern FORMAT = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TableToken() {
    }

    public static String generate() {
        StringBuilder token = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            token.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return token.toString();
    }

    /**
     * Cleans up what a person typed or pasted: ignores case, spaces and dashes, and reads O as 0 and I or L as 1.
     * Returns empty when the result is not a well-formed token.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (char c : raw.trim().toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c) || c == '-') {
                continue;
            }
            cleaned.append(switch (c) {
                case 'O' -> '0';
                case 'I', 'L' -> '1';
                default -> c;
            });
        }
        String token = cleaned.toString();
        return FORMAT.matcher(token).matches() ? Optional.of(token) : Optional.empty();
    }

    /** Accepts either a bare code or a whole table link such as https://host/t/CODE and returns the clean token. */
    public static Optional<String> extract(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String text = input.trim();
        int marker = text.lastIndexOf("/t/");
        if (marker >= 0) {
            text = text.substring(marker + 3);
            for (char stop : new char[] {'?', '#', '/'}) {
                int end = text.indexOf(stop);
                if (end >= 0) {
                    text = text.substring(0, end);
                }
            }
        }
        return normalize(text);
    }

    /** SHA-256 of a clean token, as 64 lower-case hex characters. */
    public static String hash(String cleanToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cleanToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Groups of four with dashes, for printing under the QR code: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XX. */
    public static String display(String cleanToken) {
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < cleanToken.length(); i += 4) {
            if (i > 0) {
                grouped.append('-');
            }
            grouped.append(cleanToken, i, Math.min(cleanToken.length(), i + 4));
        }
        return grouped.toString();
    }
}
