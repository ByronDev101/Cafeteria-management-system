package ke.ac.kca.cafeteria.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.CustomerOrderRepository;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.reporting.AuditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentServiceTest {

    private PaymentRecordRepository payments;
    private CustomerOrderRepository orders;
    private AuditService audit;
    private PaymentService service;
    private AppUser amina;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRecordRepository.class);
        orders = mock(CustomerOrderRepository.class);
        audit = mock(AuditService.class);
        service = new PaymentService(payments, orders, audit);
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

    private CustomerOrder order(PaymentMethod method) {
        CustomerOrder order = new CustomerOrder("SC-ABC123", amina, 5L, "Table 7", method, "key-12345678");
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 2);
        order.addItem(2L, "Samosa", new BigDecimal("30.00"), 1);
        ReflectionTestUtils.setField(order, "id", 11L);
        when(orders.findByPublicId(order.getPublicId())).thenReturn(Optional.of(order));
        return order;
    }

    /** A stored payment record for the order, moved to the given status. */
    private PaymentRecord recordIn(CustomerOrder order, PaymentStatus target) {
        PaymentRecord record = new PaymentRecord(order);
        switch (target) {
            case PENDING -> { }
            case VERIFIED -> record.markReceived("sam", null);
            case VOIDED -> record.markVoided();
            case REFUND_DUE -> {
                record.markReceived("sam", null);
                record.markRefundDue();
            }
            case REFUNDED -> {
                record.markReceived("sam", null);
                record.markRefundDue();
                record.markRefunded();
            }
        }
        when(payments.findByOrder_Id(11L)).thenReturn(Optional.of(record));
        return record;
    }

    // ---- opening ----

    @Test
    void openingARecordCopiesTheOrderAndIsLabelledAsAPrototype() {
        CustomerOrder order = order(PaymentMethod.MPESA_TILL);

        PaymentRecord record = service.openFor(order);

        verify(payments).save(record);
        assertEquals(PaymentStatus.PENDING, record.getStatus());
        assertEquals(new BigDecimal("130.00"), record.getAmount());
        assertEquals("MPESA_TILL", record.getMethodCode());
        assertTrue(record.isPrototype());
        verify(audit).record(eq("amina"), eq("PAYMENT_OPENED"), eq("PAYMENT"), eq(record.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("SC-ABC123") && details.contains("130.00")
                        && details.contains("prototype") && details.contains("no real money")));
    }

    @Test
    void openingTwiceReturnsTheExistingRecordWithoutAnotherAuditEntry() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord existing = recordIn(order, PaymentStatus.PENDING);

        assertSame(existing, service.openFor(order));

        verify(payments, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    // ---- following a cancellation ----

    @Test
    void cancellingBeforePaymentVoidsTheRecord() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(order, PaymentStatus.PENDING);

        service.settleOnCancellation("amina", order);

        assertEquals(PaymentStatus.VOIDED, record.getStatus());
        verify(audit).record(eq("amina"), eq("PAYMENT_VOIDED"), eq("PAYMENT"), eq(record.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("PENDING -> VOIDED") && details.contains("SC-ABC123")));
    }

    @Test
    void cancellingAfterPaymentMakesARefundDue() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(order, PaymentStatus.VERIFIED);

        service.settleOnCancellation("sam", order);

        assertEquals(PaymentStatus.REFUND_DUE, record.getStatus());
        verify(audit).record(eq("sam"), eq("PAYMENT_REFUND_DUE"), eq("PAYMENT"), eq(record.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("VERIFIED -> REFUND_DUE") && details.contains("130.00")));
    }

    @Test
    void settlingAnAlreadySettledPaymentChangesNothing() {
        for (PaymentStatus status : List.of(PaymentStatus.VOIDED, PaymentStatus.REFUND_DUE, PaymentStatus.REFUNDED)) {
            CustomerOrder order = order(PaymentMethod.CASH);
            PaymentRecord record = recordIn(order, status);

            service.settleOnCancellation("sam", order);

            assertEquals(status, record.getStatus());
        }
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anOrderWithNoPaymentRecordIsLeftAlone() {
        CustomerOrder order = order(PaymentMethod.CASH);

        service.settleOnCancellation("sam", order);

        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    // ---- receiving a payment ----

    @Test
    void staffCanMarkAPaymentReceivedWithAReference() {
        CustomerOrder order = order(PaymentMethod.MPESA_TILL);
        PaymentRecord record = recordIn(order, PaymentStatus.PENDING);

        PaymentResult result = service.verify("sam", order.getPublicId(), "  SJK3H2L9PQ ");

        assertEquals("SC-ABC123", result.orderNumber());
        assertEquals(PaymentStatus.VERIFIED, result.newStatus());
        assertEquals(PaymentStatus.VERIFIED, record.getStatus());
        assertEquals("SJK3H2L9PQ", record.getReference());
        assertEquals("sam", record.getVerifiedBy());
        assertNotNull(record.getVerifiedAt());
        verify(audit).record(eq("sam"), eq("PAYMENT_VERIFIED"), eq("PAYMENT"), eq(record.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("PENDING -> VERIFIED") && details.contains("reference=SJK3H2L9PQ")
                        && details.contains("no real money")));
    }

    @Test
    void theReferenceIsOptional() {
        for (String blank : new String[] {null, "", "   "}) {
            CustomerOrder order = order(PaymentMethod.CASH);
            PaymentRecord record = recordIn(order, PaymentStatus.PENDING);

            service.verify("sam", order.getPublicId(), blank);

            assertEquals(PaymentStatus.VERIFIED, record.getStatus());
            assertNull(record.getReference());
        }
    }

    @Test
    void aReferenceOfFortyCharactersIsFineAndFortyOneIsRefused() {
        CustomerOrder fine = order(PaymentMethod.MPESA_TILL);
        PaymentRecord okRecord = recordIn(fine, PaymentStatus.PENDING);
        service.verify("sam", fine.getPublicId(), "A".repeat(40));
        assertEquals("A".repeat(40), okRecord.getReference());

        CustomerOrder tooLong = order(PaymentMethod.MPESA_TILL);
        PaymentRecord record = recordIn(tooLong, PaymentStatus.PENDING);
        OrderException e = assertThrows(OrderException.class,
                () -> service.verify("sam", tooLong.getPublicId(), "A".repeat(41)));
        assertTrue(e.getMessage().contains("at most 40"));
        assertEquals(PaymentStatus.PENDING, record.getStatus());
        assertNull(record.getReference());
    }

    @Test
    void controlCharactersAndExtraSpacesInAReferenceAreCleanedUp() {
        assertEquals("AB CD EF", PaymentService.cleanReference("AB\nCD\t\tEF"));
        assertEquals("AB CD", PaymentService.cleanReference("  AB     CD  "));
        assertNull(PaymentService.cleanReference("\n\t "));
    }

    @Test
    void aPaymentAlreadyReceivedCannotBeReceivedAgain() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(order, PaymentStatus.VERIFIED);

        OrderException e = assertThrows(OrderException.class,
                () -> service.verify("sam", order.getPublicId(), "AGAIN"));

        assertTrue(e.getMessage().contains("already marked as received"));
        assertEquals(PaymentStatus.VERIFIED, record.getStatus());
        assertNull(record.getReference());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aCancelledOrdersPaymentCannotBeMarkedReceived() {
        CustomerOrder voidedOrder = order(PaymentMethod.CASH);
        PaymentRecord voided = recordIn(voidedOrder, PaymentStatus.VOIDED);
        OrderException e1 = assertThrows(OrderException.class,
                () -> service.verify("sam", voidedOrder.getPublicId(), null));
        assertTrue(e1.getMessage().contains("cancelled before payment"));
        assertEquals(PaymentStatus.VOIDED, voided.getStatus());

        for (PaymentStatus status : List.of(PaymentStatus.REFUND_DUE, PaymentStatus.REFUNDED)) {
            CustomerOrder order = order(PaymentMethod.CASH);
            PaymentRecord record = recordIn(order, status);
            OrderException e = assertThrows(OrderException.class,
                    () -> service.verify("sam", order.getPublicId(), null));
            assertTrue(e.getMessage().contains("no longer be marked as received"));
            assertEquals(status, record.getStatus());
        }
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    // ---- paying a refund ----

    @Test
    void staffCanMarkARefundPaid() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(order, PaymentStatus.REFUND_DUE);

        PaymentResult result = service.markRefunded("sam", order.getPublicId());

        assertEquals(PaymentStatus.REFUNDED, result.newStatus());
        assertEquals(PaymentStatus.REFUNDED, record.getStatus());
        verify(audit).record(eq("sam"), eq("PAYMENT_REFUNDED"), eq("PAYMENT"), eq(record.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("REFUND_DUE -> REFUNDED") && details.contains("130.00")));
    }

    @Test
    void onlyAPaymentWithARefundDueCanBeMarkedRefunded() {
        for (PaymentStatus status : List.of(PaymentStatus.PENDING, PaymentStatus.VERIFIED, PaymentStatus.VOIDED)) {
            CustomerOrder order = order(PaymentMethod.CASH);
            PaymentRecord record = recordIn(order, status);
            OrderException e = assertThrows(OrderException.class,
                    () -> service.markRefunded("sam", order.getPublicId()));
            assertTrue(e.getMessage().contains("has no refund due"), status.name());
            assertEquals(status, record.getStatus());
        }

        CustomerOrder paidBack = order(PaymentMethod.CASH);
        PaymentRecord done = recordIn(paidBack, PaymentStatus.REFUNDED);
        OrderException again = assertThrows(OrderException.class,
                () -> service.markRefunded("sam", paidBack.getPublicId()));
        assertTrue(again.getMessage().contains("already marked as paid"));
        assertEquals(PaymentStatus.REFUNDED, done.getStatus());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownOrdersAndMissingRecordsAreReportedPlainly() {
        assertEquals("That order was not found.",
                assertThrows(OrderException.class, () -> service.verify("sam", "nope", null)).getMessage());
        assertEquals("That order was not found.",
                assertThrows(OrderException.class, () -> service.markRefunded("sam", "nope")).getMessage());

        CustomerOrder noRecord = order(PaymentMethod.CASH);
        assertEquals("No payment record was found for order SC-ABC123.",
                assertThrows(OrderException.class, () -> service.verify("sam", noRecord.getPublicId(), null)).getMessage());
    }

    // ---- who may do this ----

    @Test
    void managersAreAllowedAndEveryoneElseIsTurnedAway() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(order, PaymentStatus.PENDING);

        for (String role : List.of("ROLE_STUDENT", "ROLE_ADMIN")) {
            signInAs(role);
            assertThrows(AccessDeniedException.class, () -> service.verify("x", order.getPublicId(), null), role);
            assertThrows(AccessDeniedException.class, () -> service.markRefunded("x", order.getPublicId()), role);
            assertThrows(AccessDeniedException.class, () -> service.refundsDue(100), role);
            assertThrows(AccessDeniedException.class, () -> service.refundsDueCount(), role);
            assertThrows(AccessDeniedException.class, () -> service.pollPart(), role);
        }
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.verify("x", order.getPublicId(), null));
        assertThrows(AccessDeniedException.class, () -> service.markRefunded("x", order.getPublicId()));
        assertEquals(PaymentStatus.PENDING, record.getStatus());

        signInAs("ROLE_MANAGER");
        service.verify("boss", order.getPublicId(), null);
        assertEquals(PaymentStatus.VERIFIED, record.getStatus());
    }

    // ---- reads ----

    @Test
    void refundsDueComeBackAsOrdersAndAreBounded() {
        CustomerOrder first = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(first, PaymentStatus.REFUND_DUE);
        when(payments.findByStatusWithOrder(eq("REFUND_DUE"), any(Pageable.class))).thenReturn(List.of(record));

        List<CustomerOrder> due = service.refundsDue(100);

        assertEquals(1, due.size());
        assertSame(first, due.get(0));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(payments).findByStatusWithOrder(eq("REFUND_DUE"), page.capture());
        assertEquals(100, page.getValue().getPageSize());
    }

    @Test
    void refundsDueAreCountedByStatus() {
        when(payments.countByStatusCode("REFUND_DUE")).thenReturn(4L);
        assertEquals(4L, service.refundsDueCount());
    }

    @Test
    void viewsAreKeyedByTheOrdersId() {
        CustomerOrder order = order(PaymentMethod.CASH);
        PaymentRecord record = recordIn(order, PaymentStatus.PENDING);
        when(payments.findByOrder_IdIn(any())).thenReturn(List.of(record));

        Map<Long, PaymentView> views = service.viewsFor(List.of(order));

        assertEquals(1, views.size());
        assertEquals("PENDING", views.get(11L).getStatusCode());
    }

    @Test
    void ordersThatAreNotSavedYetAreNotLookedUp() {
        CustomerOrder unsaved = new CustomerOrder("SC-NEW111", amina, 5L, "Table 7", PaymentMethod.CASH, null);

        assertTrue(service.viewsFor(List.of(unsaved)).isEmpty());

        verify(payments, never()).findByOrder_IdIn(any());
    }

    @Test
    void aSingleOrdersViewIsNullWhenItHasNoRecord() {
        CustomerOrder order = order(PaymentMethod.CASH);
        assertNull(service.viewFor(order));

        recordIn(order, PaymentStatus.VERIFIED);
        assertEquals("VERIFIED", service.viewFor(order).getStatusCode());
    }

    @Test
    void thePollPartChangesWhenAPaymentChangesAndIsEmptyWithNone() {
        assertEquals("", service.pollPart());

        Instant t1 = Instant.parse("2026-10-19T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-19T10:05:00Z");
        when(payments.statusSummary()).thenReturn(List.<Object[]>of(
                new Object[] {"VERIFIED", 2L, t1}, new Object[] {"PENDING", 1L, t2}));
        String before = service.pollPart();
        assertTrue(before.contains("PAY-VERIFIED:2:"));
        assertTrue(before.contains("PAY-PENDING:1:"));
        assertEquals(before, service.pollPart());

        when(payments.statusSummary()).thenReturn(List.<Object[]>of(
                new Object[] {"VERIFIED", 1L, t2}, new Object[] {"REFUND_DUE", 1L, t2}, new Object[] {"PENDING", 1L, t2}));
        assertNotEquals(before, service.pollPart());
        assertFalse(service.pollPart().isEmpty());
    }
}
