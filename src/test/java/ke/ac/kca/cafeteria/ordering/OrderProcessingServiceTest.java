package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.reporting.AuditService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class OrderProcessingServiceTest {

    private CustomerOrderRepository orders;
    private AuditService audit;
    private OrderProcessingService service;
    private AppUser amina;

    @BeforeEach
    void setUp() {
        orders = mock(CustomerOrderRepository.class);
        audit = mock(AuditService.class);
        service = new OrderProcessingService(orders, audit, "Africa/Nairobi");
        amina = new AppUser("amina", "hash", "Amina Wanjiru");
        signInAs("ROLE_STAFF");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("sam", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private CustomerOrder newOrder() {
        CustomerOrder order = new CustomerOrder("SC-ABC123", amina, 5L, "Table 7", PaymentMethod.CASH, "key-12345678");
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 2);
        order.addItem(2L, "Samosa", new BigDecimal("30.00"), 1);
        when(orders.findByPublicId(order.getPublicId())).thenReturn(Optional.of(order));
        return order;
    }

    private CustomerOrder orderIn(OrderStatus target) {
        CustomerOrder order = newOrder();
        if (target == OrderStatus.CANCELLED) {
            order.moveTo(OrderStatus.CANCELLED, "sam", "Out of stock");
            return order;
        }
        for (OrderStatus step : OrderStatus.PROGRESS) {
            if (step == OrderStatus.SUBMITTED) {
                continue;
            }
            if (order.getStatus() == target) {
                break;
            }
            order.moveTo(step, "sam", null);
        }
        return order;
    }

    // ---- the board ----

    @Test
    void theBoardShowsWhatIsNeededToServeAnOrderAndTheStudentsDisplayName() {
        CustomerOrder order = newOrder();
        when(orders.findByStatusCodeInOrderByCreatedAtAsc(eq(List.of("SUBMITTED", "ACCEPTED", "PREPARING", "READY")),
                any(Pageable.class))).thenReturn(List.of(order));

        QueueOrder q = service.queue(QueueFilter.ACTIVE).get(0);

        assertEquals("SC-ABC123", q.getOrderNumber());
        assertEquals("Table 7", q.getTableLabel());
        assertEquals("Amina Wanjiru", q.getStudentName());
        assertEquals(List.of("2 x Tea", "1 x Samosa"), q.getLines());
        assertEquals("Cash", q.getPaymentLabel());
        assertEquals("KSh 130.00", q.getTotalText());
        assertEquals("ACCEPTED", q.getNextStatusCode());
        assertEquals("Accept order", q.getNextActionLabel());
        assertTrue(q.isCanCancel());
        assertEquals("just now", q.getWaitText());
        assertEquals(0L, q.getVersion());

        for (Method method : QueueOrder.class.getMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("username") || name.contains("email") || name.contains("account")
                            || name.contains("password"),
                    "the board shows the display name only, never login details: " + method.getName());
        }
    }

    @Test
    void finishedOrdersShowNoButtonsAndExplainACancellation() {
        CustomerOrder completed = orderIn(OrderStatus.COMPLETED);
        CustomerOrder byStaff = orderIn(OrderStatus.CANCELLED);
        CustomerOrder byStudent = newOrder();
        byStudent.moveTo(OrderStatus.CANCELLED, "amina", "Cancelled by the student");
        when(orders.findByStatusCodeInAndUpdatedAtAfterOrderByUpdatedAtDesc(eq(List.of("COMPLETED", "CANCELLED")),
                any(Instant.class), any(Pageable.class))).thenReturn(List.of(completed, byStaff, byStudent));

        List<QueueOrder> board = service.queue(QueueFilter.DONE_TODAY);

        assertNull(board.get(0).getNextStatusCode());
        assertFalse(board.get(0).isCanCancel());
        assertNull(board.get(0).getCancelNote());
        assertEquals("Cancelled by staff: Out of stock", board.get(1).getCancelNote());
        assertEquals("Cancelled by the student", board.get(2).getCancelNote());
        assertFalse(board.get(2).isCanCancel());
    }

    @Test
    void eachStatusOffersExactlyOneForwardButton() {
        assertEquals(OrderStatus.ACCEPTED, OrderProcessingService.forwardStep(OrderStatus.SUBMITTED));
        assertEquals(OrderStatus.PREPARING, OrderProcessingService.forwardStep(OrderStatus.ACCEPTED));
        assertEquals(OrderStatus.READY, OrderProcessingService.forwardStep(OrderStatus.PREPARING));
        assertEquals(OrderStatus.COMPLETED, OrderProcessingService.forwardStep(OrderStatus.READY));
        assertNull(OrderProcessingService.forwardStep(OrderStatus.COMPLETED));
        assertNull(OrderProcessingService.forwardStep(OrderStatus.CANCELLED));
        assertEquals("Accept order", OrderProcessingService.actionLabel(OrderStatus.ACCEPTED));
        assertEquals("Start preparing", OrderProcessingService.actionLabel(OrderStatus.PREPARING));
        assertEquals("Mark ready", OrderProcessingService.actionLabel(OrderStatus.READY));
        assertEquals("Served: complete", OrderProcessingService.actionLabel(OrderStatus.COMPLETED));
    }

    @Test
    void waitTimeIsPutInWords() {
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        assertEquals("just now", OrderProcessingService.waitText(now, now));
        assertEquals("just now", OrderProcessingService.waitText(now.plusSeconds(30), now));
        assertEquals("7 min ago", OrderProcessingService.waitText(now.minusSeconds(7 * 60), now));
        assertEquals("1 h 5 min ago", OrderProcessingService.waitText(now.minusSeconds(65 * 60), now));
        assertEquals("2 h ago", OrderProcessingService.waitText(now.minusSeconds(120 * 60), now));
    }

    @Test
    void tabCountsAndThePollTokenComeFromOneSummary() {
        Instant t1 = Instant.parse("2026-10-19T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-19T10:05:00Z");
        when(orders.statusSummary()).thenReturn(List.<Object[]>of(
                new Object[] {"SUBMITTED", 2L, t1}, new Object[] {"READY", 1L, t2}, new Object[] {"COMPLETED", 9L, t1}));

        Map<QueueFilter, Long> counts = service.counts();
        assertEquals(3L, counts.get(QueueFilter.ACTIVE));
        assertEquals(2L, counts.get(QueueFilter.NEW));
        assertEquals(0L, counts.get(QueueFilter.KITCHEN));
        assertEquals(1L, counts.get(QueueFilter.READY));
        assertFalse(counts.containsKey(QueueFilter.DONE_TODAY));

        String before = service.pollToken();
        assertTrue(before.contains("SUBMITTED:2:"));
        assertEquals(before, service.pollToken());

        when(orders.statusSummary()).thenReturn(List.<Object[]>of(
                new Object[] {"SUBMITTED", 1L, t2}, new Object[] {"ACCEPTED", 1L, t2}, new Object[] {"READY", 1L, t2},
                new Object[] {"COMPLETED", 9L, t1}));
        assertNotEquals(before, service.pollToken());
    }

    // ---- changing status ----

    @Test
    void acceptingAnOrderRecordsWhoDidItAndAudits() {
        CustomerOrder order = newOrder();
        TransitionResult result = service.transition("sam", order.getPublicId(), "ACCEPTED", null, 0L);

        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertEquals("SC-ABC123", result.orderNumber());
        assertEquals(OrderStatus.ACCEPTED, result.newStatus());
        assertEquals(2, order.getHistory().size());
        assertEquals("sam", order.getHistory().get(1).getActorUsername());
        assertEquals(OrderStatus.SUBMITTED, order.getHistory().get(1).getFromStatus());
        verify(audit).record(eq("sam"), eq("ORDER_STATUS_CHANGED"), eq("ORDER"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("SUBMITTED -> ACCEPTED") && details.contains("SC-ABC123")));
    }

    @Test
    void anOrderCanBeWalkedAllTheWayToCompleted() {
        CustomerOrder order = newOrder();
        for (String step : List.of("ACCEPTED", "PREPARING", "READY", "COMPLETED")) {
            service.transition("sam", order.getPublicId(), step, null, null);
        }
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(5, order.getHistory().size());
    }

    @Test
    void anIllegalMoveIsRefusedAndNothingChanges() {
        CustomerOrder order = newOrder();
        OrderException e = assertThrows(OrderException.class,
                () -> service.transition("sam", order.getPublicId(), "READY", null, 0L));
        assertTrue(e.getMessage().contains("cannot be moved"));
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
        assertEquals(1, order.getHistory().size());
        verify(audit, never()).record(any(), eq("ORDER_STATUS_CHANGED"), any(), any(), any(), any());
    }

    @Test
    void finishedOrdersCannotMoveAgain() {
        CustomerOrder done = orderIn(OrderStatus.COMPLETED);
        CustomerOrder cancelled = orderIn(OrderStatus.CANCELLED);
        for (String target : List.of("ACCEPTED", "PREPARING", "READY", "COMPLETED", "CANCELLED")) {
            assertThrows(OrderException.class, () -> service.transition("sam", done.getPublicId(), target, "reason here", null));
            assertThrows(OrderException.class, () -> service.transition("sam", cancelled.getPublicId(), target, "reason here", null));
        }
        assertEquals(OrderStatus.COMPLETED, done.getStatus());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancellingNeedsASensibleReason() {
        CustomerOrder order = newOrder();
        for (String bad : new String[] {null, "", "  ", "ab", "x".repeat(256)}) {
            assertThrows(OrderException.class, () -> service.transition("sam", order.getPublicId(), "CANCELLED", bad, null));
        }
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());

        service.transition("sam", order.getPublicId(), "CANCELLED", "  Out   of stock ", 0L);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("Out of stock", order.getHistory().get(1).getReason());
        verify(audit).record(eq("sam"), eq("ORDER_STATUS_CHANGED"), eq("ORDER"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("reason=Out of stock")));
    }

    @Test
    void aStaleScreenCannotOverwriteNewerWork() {
        CustomerOrder order = newOrder();
        OrderException e = assertThrows(OrderException.class,
                () -> service.transition("sam", order.getPublicId(), "ACCEPTED", null, 5L));
        assertTrue(e.getMessage().contains("changed by someone else"));
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
    }

    @Test
    void unknownOrdersAndBadStatusesAreRefused() {
        CustomerOrder order = newOrder();
        when(orders.findByPublicId("nope")).thenReturn(Optional.empty());
        assertEquals("That order was not found.",
                assertThrows(OrderException.class, () -> service.transition("sam", "nope", "ACCEPTED", null, null)).getMessage());
        assertEquals("Choose a valid status.",
                assertThrows(OrderException.class, () -> service.transition("sam", order.getPublicId(), "SHIPPED", null, null)).getMessage());
        assertThrows(OrderException.class, () -> service.transition("sam", order.getPublicId(), null, null, null));
        assertThrows(OrderException.class, () -> service.transition("sam", order.getPublicId(), "accepted", null, null));
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
    }

    // ---- who may do this ----

    @Test
    void managersAreAllowedAndEveryoneElseIsTurnedAway() {
        CustomerOrder order = newOrder();

        signInAs("ROLE_MANAGER");
        service.transition("boss", order.getPublicId(), "ACCEPTED", null, null);
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());

        for (String role : List.of("ROLE_STUDENT", "ROLE_ADMIN")) {
            signInAs(role);
            assertThrows(AccessDeniedException.class, () -> service.queue(QueueFilter.ACTIVE), role);
            assertThrows(AccessDeniedException.class, () -> service.counts(), role);
            assertThrows(AccessDeniedException.class, () -> service.pollToken(), role);
            assertThrows(AccessDeniedException.class,
                    () -> service.transition("x", order.getPublicId(), "PREPARING", null, null), role);
        }
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());

        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.queue(QueueFilter.ACTIVE));
        assertThrows(AccessDeniedException.class,
                () -> service.transition("x", order.getPublicId(), "PREPARING", null, null));
    }
}
