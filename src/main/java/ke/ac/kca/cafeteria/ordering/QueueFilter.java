package ke.ac.kca.cafeteria.ordering;

import java.util.List;
import java.util.Locale;

/** The views of the staff order board. Finished orders are shown only for the current day. */
public enum QueueFilter {

    ACTIVE("All active", List.of(OrderStatus.SUBMITTED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)),
    NEW("New", List.of(OrderStatus.SUBMITTED)),
    KITCHEN("In the kitchen", List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING)),
    READY("Ready to serve", List.of(OrderStatus.READY)),
    DONE_TODAY("Finished today", List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)),
    /** Cancelled orders whose payment was received and now has to be handed back, whatever day they were cancelled. */
    REFUNDS("Refunds due", List.of());

    private final String label;
    private final List<OrderStatus> statuses;

    QueueFilter(String label, List<OrderStatus> statuses) {
        this.label = label;
        this.statuses = statuses;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return name();
    }

    public List<OrderStatus> statuses() {
        return statuses;
    }

    public List<String> statusCodes() {
        return statuses.stream().map(Enum::name).toList();
    }

    /** True for the view that lists finished orders, which are limited to today. */
    public boolean isFinishedView() {
        return this == DONE_TODAY;
    }

    /** True for the view of refunds still to be handed back. It is chosen by payment status, not by order status. */
    public boolean isRefundView() {
        return this == REFUNDS;
    }

    /** Anything unrecognised falls back to the all-active view. */
    public static QueueFilter parse(String code) {
        if (code != null) {
            String wanted = code.trim().toUpperCase(Locale.ROOT);
            for (QueueFilter filter : values()) {
                if (filter.name().equals(wanted)) {
                    return filter;
                }
            }
        }
        return ACTIVE;
    }
}
