package ke.ac.kca.cafeteria.reporting;

/**
 * One order in the report search. It shows what a manager needs to find an order: number, time, table, status,
 * total and payment. It deliberately leaves out who ordered it.
 */
public final class ReportRow {

    private final String orderNumber;
    private final String createdText;
    private final String tableLabel;
    private final String statusLabel;
    private final String statusClass;
    private final String totalText;
    private final String methodLabel;
    private final String paymentLabel;
    private final String paymentClass;

    public ReportRow(String orderNumber, String createdText, String tableLabel, String statusLabel, String statusClass,
                     String totalText, String methodLabel, String paymentLabel, String paymentClass) {
        this.orderNumber = orderNumber;
        this.createdText = createdText;
        this.tableLabel = tableLabel;
        this.statusLabel = statusLabel;
        this.statusClass = statusClass;
        this.totalText = totalText;
        this.methodLabel = methodLabel;
        this.paymentLabel = paymentLabel;
        this.paymentClass = paymentClass;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCreatedText() {
        return createdText;
    }

    public String getTableLabel() {
        return tableLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusClass() {
        return statusClass;
    }

    public String getTotalText() {
        return totalText;
    }

    public String getMethodLabel() {
        return methodLabel;
    }

    /** The payment status in words, or null when the order has no payment record. */
    public String getPaymentLabel() {
        return paymentLabel;
    }

    public String getPaymentClass() {
        return paymentClass;
    }
}
