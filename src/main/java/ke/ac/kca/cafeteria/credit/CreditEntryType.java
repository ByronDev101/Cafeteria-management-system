package ke.ac.kca.cafeteria.credit;

import java.util.Optional;

/**
 * The kinds of line in the credit ledger. Amounts are always positive; the type says what the line does:
 * RESERVE puts an order's total on hold, RELEASE gives a hold back, POST turns a hold into a charge (when staff accept
 * the order), REVERSAL cancels a charge (an order cancelled after acceptance), and SETTLEMENT records that the
 * student paid some of what they owe.
 */
public enum CreditEntryType {

    RESERVE("Held for an order"),
    RELEASE("Hold released"),
    POST("Charged to your credit"),
    REVERSAL("Charge cancelled"),
    SETTLEMENT("Payment recorded");

    private final String label;

    CreditEntryType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<CreditEntryType> parse(String code) {
        if (code != null) {
            for (CreditEntryType type : values()) {
                if (type.name().equals(code.trim())) {
                    return Optional.of(type);
                }
            }
        }
        return Optional.empty();
    }
}
