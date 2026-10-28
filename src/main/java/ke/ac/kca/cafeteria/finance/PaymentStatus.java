package ke.ac.kca.cafeteria.finance;

import java.util.Optional;

/**
 * Where an order's payment record stands (FR-023 to FR-026). The only moves are
 * PENDING to VERIFIED or VOIDED, VERIFIED to REFUND_DUE, and REFUND_DUE to REFUNDED.
 * VOIDED and REFUNDED are final. A refund is just a status step, not a new record.
 */
public enum PaymentStatus {

    PENDING("Awaiting payment"),
    VERIFIED("Payment received"),
    VOIDED("No payment taken"),
    REFUND_DUE("Refund due"),
    REFUNDED("Refunded");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return name();
    }

    public boolean canMoveTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == VERIFIED || next == VOIDED;
            case VERIFIED -> next == REFUND_DUE;
            case REFUND_DUE -> next == REFUNDED;
            case VOIDED, REFUNDED -> false;
        };
    }

    public static Optional<PaymentStatus> parse(String code) {
        if (code != null) {
            for (PaymentStatus status : values()) {
                if (status.name().equals(code.trim())) {
                    return Optional.of(status);
                }
            }
        }
        return Optional.empty();
    }
}
