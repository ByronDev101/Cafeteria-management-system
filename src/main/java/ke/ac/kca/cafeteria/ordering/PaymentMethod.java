package ke.ac.kca.cafeteria.ordering;

import java.util.List;
import java.util.Optional;

/**
 * How a student says they will pay (FR-015). Payments are recorded offline by staff; no card, mobile-money PIN or
 * bank detail is ever collected. CREDIT is reserved for Sprint 5 and is not offered yet.
 */
public enum PaymentMethod {

    CASH("Cash", "Pay with cash when your order is served."),
    MPESA_TILL("M-Pesa till", "Pay to the cafeteria's M-Pesa till. Staff confirm your payment."),
    CREDIT("Cafeteria credit", "Charge to your approved cafeteria credit.");

    private final String label;
    private final String hint;

    PaymentMethod(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getHint() {
        return hint;
    }

    /** The methods a student can choose today. */
    public static List<PaymentMethod> offered() {
        return List.of(CASH, MPESA_TILL);
    }

    public static Optional<PaymentMethod> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (PaymentMethod method : values()) {
            if (method.name().equals(code.trim())) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
