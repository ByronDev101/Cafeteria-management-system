package ke.ac.kca.cafeteria.ordering;

/** What a status change did, so the board can say it in plain words. */
public record TransitionResult(String orderNumber, OrderStatus newStatus) {
}
