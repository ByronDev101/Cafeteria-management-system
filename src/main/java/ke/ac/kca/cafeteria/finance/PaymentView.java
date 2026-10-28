package ke.ac.kca.cafeteria.finance;

import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import java.util.Locale;

/**
 * A payment as a screen needs it: plain text and yes/no flags, no entity. Staff see the buttons that apply; students
 * see a short note. Nothing here claims that real money moved.
 */
public final class PaymentView {

    private final String statusCode;
    private final String statusLabel;
    private final String reference;
    private final boolean canVerify;
    private final boolean canMarkRefunded;
    private final boolean askReference;
    private final String studentNote;

    public PaymentView(String statusCode, String statusLabel, String reference, boolean canVerify,
                       boolean canMarkRefunded, boolean askReference, String studentNote) {
        this.statusCode = statusCode;
        this.statusLabel = statusLabel;
        this.reference = reference;
        this.canVerify = canVerify;
        this.canMarkRefunded = canMarkRefunded;
        this.askReference = askReference;
        this.studentNote = studentNote;
    }

    public static PaymentView of(PaymentRecord record) {
        PaymentStatus status = record.getStatus();
        return new PaymentView(status.name(), status.getLabel(), record.getReference(),
                status == PaymentStatus.PENDING, status == PaymentStatus.REFUND_DUE,
                record.getPaymentMethod() == PaymentMethod.MPESA_TILL, studentNote(status));
    }

    private static String studentNote(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "Staff write down your payment when they receive it.";
            case VERIFIED -> "Staff have written down your payment as received.";
            case VOIDED -> "Nothing was paid for this order.";
            case REFUND_DUE -> "Staff will return your payment. Ask at the counter if you have not received it.";
            case REFUNDED -> "Staff have written down your refund as paid.";
        };
    }

    public String getStatusCode() {
        return statusCode;
    }

    /** Lower-case, dash-separated status for styling, for example "refund-due". */
    public String getStatusClass() {
        return statusCode.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getReference() {
        return reference;
    }

    public boolean isCanVerify() {
        return canVerify;
    }

    public boolean isCanMarkRefunded() {
        return canMarkRefunded;
    }

    /** True when the payment goes to the M-Pesa till, so the board offers a box for the M-Pesa code. */
    public boolean isAskReference() {
        return askReference;
    }

    public String getStudentNote() {
        return studentNote;
    }
}
