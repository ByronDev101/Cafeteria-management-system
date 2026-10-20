package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueueFilterTest {

    @Test
    void eachViewCoversTheRightStatuses() {
        assertEquals(List.of("SUBMITTED", "ACCEPTED", "PREPARING", "READY"), QueueFilter.ACTIVE.statusCodes());
        assertEquals(List.of("SUBMITTED"), QueueFilter.NEW.statusCodes());
        assertEquals(List.of("ACCEPTED", "PREPARING"), QueueFilter.KITCHEN.statusCodes());
        assertEquals(List.of("READY"), QueueFilter.READY.statusCodes());
        assertEquals(List.of("COMPLETED", "CANCELLED"), QueueFilter.DONE_TODAY.statusCodes());
    }

    @Test
    void onlyTheFinishedViewIsLimitedToToday() {
        assertTrue(QueueFilter.DONE_TODAY.isFinishedView());
        assertFalse(QueueFilter.ACTIVE.isFinishedView());
        assertFalse(QueueFilter.READY.isFinishedView());
    }

    @Test
    void unknownOrMissingFiltersFallBackToAllActive() {
        assertEquals(QueueFilter.NEW, QueueFilter.parse("new"));
        assertEquals(QueueFilter.DONE_TODAY, QueueFilter.parse(" DONE_TODAY "));
        assertEquals(QueueFilter.ACTIVE, QueueFilter.parse("bogus"));
        assertEquals(QueueFilter.ACTIVE, QueueFilter.parse(null));
        assertEquals(QueueFilter.ACTIVE, QueueFilter.parse("' OR 1=1 --"));
    }
}
