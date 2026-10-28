package ke.ac.kca.cafeteria.finance;

/** What a payment step did, so the board can say it in plain words. */
public record PaymentResult(String orderNumber, PaymentStatus newStatus) {
}
