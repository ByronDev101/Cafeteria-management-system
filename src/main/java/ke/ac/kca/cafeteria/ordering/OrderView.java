package ke.ac.kca.cafeteria.ordering;

import java.util.List;

/** A whole order for its owner: lines, totals and the progress tracker. */
public final class OrderView {

    private final String publicId;
    private final String orderNumber;
    private final String statusCode;
    private final String statusLabel;
    private final boolean terminal;
    private final boolean canCancel;
    private final String tableLabel;
    private final String paymentLabel;
    private final String totalText;
    private final String createdText;
    private final List<OrderLineView> lines;
    private final List<OrderStepView> steps;

    public OrderView(String publicId, String orderNumber, String statusCode, String statusLabel, boolean terminal,
                     boolean canCancel, String tableLabel, String paymentLabel, String totalText, String createdText,
                     List<OrderLineView> lines, List<OrderStepView> steps) {
        this.publicId = publicId;
        this.orderNumber = orderNumber;
        this.statusCode = statusCode;
        this.statusLabel = statusLabel;
        this.terminal = terminal;
        this.canCancel = canCancel;
        this.tableLabel = tableLabel;
        this.paymentLabel = paymentLabel;
        this.totalText = totalText;
        this.createdText = createdText;
        this.lines = List.copyOf(lines);
        this.steps = List.copyOf(steps);
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

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    public String getTableLabel() {
        return tableLabel;
    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    public String getTotalText() {
        return totalText;
    }

    public String getCreatedText() {
        return createdText;
    }

    public List<OrderLineView> getLines() {
        return lines;
    }

    public List<OrderStepView> getSteps() {
        return steps;
    }
}
