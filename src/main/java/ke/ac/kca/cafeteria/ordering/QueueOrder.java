package ke.ac.kca.cafeteria.ordering;

import java.util.List;

/**
 * One order on the staff board. It carries only what is needed to serve the order: the order number, time, table,
 * the student's display name, items, payment method and status. The student number, email and account are
 * deliberately not included.
 */
public final class QueueOrder {

    private final String publicId;
    private final String orderNumber;
    private final String statusCode;
    private final String statusLabel;
    private final String tableLabel;
    private final String studentName;
    private final String createdText;
    private final String waitText;
    private final List<String> lines;
    private final String totalText;
    private final String paymentLabel;
    private final long version;
    private final String nextStatusCode;
    private final String nextActionLabel;
    private final boolean canCancel;
    private final String cancelNote;

    public QueueOrder(String publicId, String orderNumber, String statusCode, String statusLabel, String tableLabel,
                      String studentName, String createdText, String waitText, List<String> lines, String totalText, String paymentLabel,
                      long version, String nextStatusCode, String nextActionLabel, boolean canCancel,
                      String cancelNote) {
        this.publicId = publicId;
        this.orderNumber = orderNumber;
        this.statusCode = statusCode;
        this.statusLabel = statusLabel;
        this.tableLabel = tableLabel;
        this.studentName = studentName;
        this.createdText = createdText;
        this.waitText = waitText;
        this.lines = List.copyOf(lines);
        this.totalText = totalText;
        this.paymentLabel = paymentLabel;
        this.version = version;
        this.nextStatusCode = nextStatusCode;
        this.nextActionLabel = nextActionLabel;
        this.canCancel = canCancel;
        this.cancelNote = cancelNote;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusClass() {
        return statusCode.toLowerCase(java.util.Locale.ROOT);
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getTableLabel() {
        return tableLabel;
    }

    /** The student's display name, so staff can call out the order. Never the student number, email or account. */
    public String getStudentName() {
        return studentName;
    }

    public String getCreatedText() {
        return createdText;
    }

    public String getWaitText() {
        return waitText;
    }

    /** Lines such as "2 x Tea". */
    public List<String> getLines() {
        return lines;
    }

    public String getTotalText() {
        return totalText;
    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    /** The version the staff member saw. It is sent back with each action so a stale click is refused. */
    public long getVersion() {
        return version;
    }

    /** The one forward step offered as a button, or null for finished orders. */
    public String getNextStatusCode() {
        return nextStatusCode;
    }

    public String getNextActionLabel() {
        return nextActionLabel;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    /** For cancelled orders: who cancelled and why. */
    public String getCancelNote() {
        return cancelNote;
    }
}
