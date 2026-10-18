package ke.ac.kca.cafeteria.ordering;

import java.util.List;

/**
 * The life of an order (FR-020): Submitted, Accepted, Preparing, Ready, Completed, with a cancellation path.
 * Orders only ever move along these arrows; anything else is rejected without changing the order.
 */
public enum OrderStatus {

    SUBMITTED("Submitted"),
    ACCEPTED("Accepted"),
    PREPARING("Preparing"),
    READY("Ready"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    /** The normal path, in order, used to draw the progress steps. */
    public static final List<OrderStatus> PROGRESS = List.of(SUBMITTED, ACCEPTED, PREPARING, READY, COMPLETED);

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Statuses a student may still be waiting on. */
    public boolean isActive() {
        return !isTerminal();
    }

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case SUBMITTED -> next == ACCEPTED || next == CANCELLED;
            case ACCEPTED -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == READY || next == CANCELLED;
            case READY -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** A student may cancel only before staff accept the order (FR-018). */
    public boolean studentMayCancel() {
        return this == SUBMITTED;
    }

    public static List<OrderStatus> activeStatuses() {
        return List.of(SUBMITTED, ACCEPTED, PREPARING, READY);
    }
}
