package ke.ac.kca.cafeteria.ordering;

/** One order in a list: enough to recognise it and see where it is. */
public final class OrderSummary {

    private final String publicId;
    private final String orderNumber;
    private final String statusCode;
    private final String statusLabel;
    private final boolean active;
    private final String totalText;
    private final String createdText;
    private final String tableLabel;
    private final String itemSummary;

    public OrderSummary(String publicId, String orderNumber, String statusCode, String statusLabel, boolean active,
                        String totalText, String createdText, String tableLabel, String itemSummary) {
        this.publicId = publicId;
        this.orderNumber = orderNumber;
        this.statusCode = statusCode;
        this.statusLabel = statusLabel;
        this.active = active;
        this.totalText = totalText;
        this.createdText = createdText;
        this.tableLabel = tableLabel;
        this.itemSummary = itemSummary;
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

    /** Lower-case status for styling, for example "preparing". */
    public String getStatusClass() {
        return statusCode.toLowerCase(java.util.Locale.ROOT);
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public boolean isActive() {
        return active;
    }

    public String getTotalText() {
        return totalText;
    }

    public String getCreatedText() {
        return createdText;
    }

    public String getTableLabel() {
        return tableLabel;
    }

    public String getItemSummary() {
        return itemSummary;
    }
}
