package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void onlyTheDocumentedMovesAreAllowed() {
        Set<String> legal = Set.of(
                "SUBMITTED>ACCEPTED", "SUBMITTED>CANCELLED", "ACCEPTED>PREPARING", "ACCEPTED>CANCELLED",
                "PREPARING>READY", "PREPARING>CANCELLED", "READY>COMPLETED", "READY>CANCELLED");
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertEquals(legal.contains(from + ">" + to), from.canMoveTo(to), from + " to " + to);
            }
        }
    }

    @Test
    void finishedOrdersNeverMoveAgain() {
        for (OrderStatus to : OrderStatus.values()) {
            assertFalse(OrderStatus.COMPLETED.canMoveTo(to));
            assertFalse(OrderStatus.CANCELLED.canMoveTo(to));
        }
        assertTrue(OrderStatus.COMPLETED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.READY.isTerminal());
    }

    @Test
    void studentsMayCancelOnlyBeforeAcceptance() {
        assertTrue(OrderStatus.SUBMITTED.studentMayCancel());
        for (OrderStatus status : List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
            assertFalse(status.studentMayCancel(), status.name());
        }
    }

    @Test
    void activeAndProgressListsAreConsistent() {
        assertEquals(List.of(OrderStatus.SUBMITTED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY),
                OrderStatus.activeStatuses());
        assertEquals(5, OrderStatus.PROGRESS.size());
        assertFalse(OrderStatus.PROGRESS.contains(OrderStatus.CANCELLED));
    }
}
