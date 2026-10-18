package ke.ac.kca.cafeteria.ordering;

import java.security.SecureRandom;

/**
 * Human-friendly order numbers such as SC-7K3M9P, easy to read out across a counter. They are display references
 * only: possessing a number never gives access to an order (SDS 5).
 */
public final class OrderNumber {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNumber() {
    }

    public static String generate() {
        StringBuilder number = new StringBuilder("SC-");
        for (int i = 0; i < LENGTH; i++) {
            number.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }
}
