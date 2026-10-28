package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.finance.PaymentService;
import ke.ac.kca.cafeteria.finance.PaymentView;
import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.identity.UserRepository;
import ke.ac.kca.cafeteria.menu.MenuService;
import ke.ac.kca.cafeteria.menu.OrderableItem;
import ke.ac.kca.cafeteria.reporting.AuditService;
import ke.ac.kca.cafeteria.table.TableRef;
import ke.ac.kca.cafeteria.table.TableService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class OrderServiceTest {

    private CustomerOrderRepository orders;
    private UserRepository users;
    private TableService tables;
    private MenuService menu;
    private AuditService audit;
    private PaymentService payments;
    private OrderService service;
    private AppUser amina;

    private static final OrderableItem TEA = new OrderableItem(1L, "tea", "Tea", new BigDecimal("50.00"), true, null, null);
    private static final OrderableItem SAMOSA = new OrderableItem(2L, "samosa", "Samosa", new BigDecimal("30.00"), true, null, null);
    private static final OrderableItem JUICE = new OrderableItem(3L, "juice", "Fresh Juice", new BigDecimal("120.00"), false, "sold out", null);

    @BeforeEach
    void setUp() {
        orders = mock(CustomerOrderRepository.class);
        users = mock(UserRepository.class);
        tables = mock(TableService.class);
        menu = mock(MenuService.class);
        audit = mock(AuditService.class);
        payments = mock(PaymentService.class);
        service = new OrderService(orders, users, tables, menu, audit, payments, "Africa/Nairobi");

        amina = new AppUser("amina", "hash", "Amina Wanjiru");
        when(users.findByUsername("amina")).thenReturn(Optional.of(amina));
        when(tables.activeTable("t1")).thenReturn(Optional.of(new TableRef(5L, "t1", "Table 7")));
        menuHas(TEA, SAMOSA);
    }

    private void menuHas(OrderableItem... items) {
        Map<String, OrderableItem> found = new LinkedHashMap<>();
        for (OrderableItem item : items) {
            found.put(item.publicId(), item);
        }
        when(menu.lookup(anyCollection())).thenReturn(found);
    }

    private static Map<String, Integer> lines(Object... pairs) {
        Map<String, Integer> lines = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            lines.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return lines;
    }

    private CustomerOrder storedOrder() {
        CustomerOrder order = new CustomerOrder("SC-ABC123", amina, 5L, "Table 7", PaymentMethod.CASH, "key-12345678");
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 2);
        order.addItem(2L, "Samosa", new BigDecimal("30.00"), 1);
        when(orders.findByPublicIdAndStudent_Username(order.getPublicId(), "amina")).thenReturn(Optional.of(order));
        return order;
    }

    // ---- placing ----

    @Test
    void placingCopiesPricesAndTableAndStartsTheHistory() {
        PlacedOrder placed = service.place("amina", lines("tea", 2, "samosa", 1), "t1", PaymentMethod.CASH, "key-12345678");

        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        CustomerOrder order = saved.getValue();

        assertFalse(placed.duplicate());
        assertEquals(order.getPublicId(), placed.publicId());
        assertTrue(order.getOrderNumber().matches("SC-[0-9A-HJKMNP-TV-Z]{6}"));
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
        assertEquals(PaymentMethod.CASH, order.getPaymentMethod());
        assertEquals(5L, order.getTableId());
        assertEquals("Table 7", order.getTableLabel());
        assertEquals(new BigDecimal("130.00"), order.getTotal());
        assertEquals(2, order.getItems().size());
        assertEquals("Tea", order.getItems().get(0).getItemName());
        assertEquals(new BigDecimal("50.00"), order.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("100.00"), order.getItems().get(0).getLineTotal());
        assertEquals(1, order.getHistory().size());
        assertEquals(OrderStatus.SUBMITTED, order.getHistory().get(0).getToStatus());
        assertEquals("amina", order.getHistory().get(0).getActorUsername());
        verify(audit).record(eq("amina"), eq("ORDER_PLACED"), eq("ORDER"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("Table 7") && details.contains("130.00")));
    }

    @Test
    void anItemThatIsNotOrderableStopsTheWholeOrder() {
        menuHas(TEA, JUICE);
        OrderException e = assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1, "juice", 1), "t1", PaymentMethod.CASH, "key-12345678"));
        assertTrue(e.getMessage().contains("Fresh Juice is sold out"));
        verify(orders, never()).save(any());
    }

    @Test
    void anItemMissingFromTheMenuStopsTheOrder() {
        menuHas(TEA);
        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1, "deleted", 1), "t1", PaymentMethod.CASH, "key-12345678"));
        verify(orders, never()).save(any());
    }

    @Test
    void badQuantitiesAreRejected() {
        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 0), "t1", PaymentMethod.CASH, "key-12345678"));
        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 11), "t1", PaymentMethod.CASH, "key-12345678"));
        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", -1), "t1", PaymentMethod.CASH, "key-12345678"));
        verify(orders, never()).save(any());
    }

    @Test
    void anOrderNeedsItemsAnActiveTableAndAnOfferedPaymentMethod() {
        OrderException empty = assertThrows(OrderException.class,
                () -> service.place("amina", lines(), "t1", PaymentMethod.CASH, "key-12345678"));
        assertTrue(empty.getMessage().contains("cart is empty"));

        OrderException noTable = assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1), null, PaymentMethod.CASH, "key-12345678"));
        assertTrue(noTable.getMessage().contains("Choose your table first"));

        when(tables.activeTable("t9")).thenReturn(Optional.empty());
        OrderException gone = assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1), "t9", PaymentMethod.CASH, "key-12345678"));
        assertTrue(gone.getMessage().contains("no longer available"));

        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1), "t1", null, "key-12345678"));
        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1), "t1", PaymentMethod.CREDIT, "key-12345678"));
        verify(orders, never()).save(any());
    }

    @Test
    void aRepeatedSubmitReturnsTheEarlierOrderInsteadOfCreatingAnother() {
        CustomerOrder earlier = storedOrder();
        when(orders.findByStudent_UsernameAndIdempotencyKey("amina", "key-12345678")).thenReturn(Optional.of(earlier));

        PlacedOrder placed = service.place("amina", lines("tea", 1), "t1", PaymentMethod.CASH, "key-12345678");

        assertTrue(placed.duplicate());
        assertEquals(earlier.getPublicId(), placed.publicId());
        verify(orders, never()).save(any());
    }

    // ---- reading: ownership ----

    @Test
    void anotherStudentsOrderIsReportedAsNotFound() {
        CustomerOrder order = storedOrder();
        assertEquals("SC-ABC123", service.detail("amina", order.getPublicId()).getOrderNumber());

        OrderException e = assertThrows(OrderException.class, () -> service.detail("brian", order.getPublicId()));
        assertEquals("That order was not found.", e.getMessage());
        assertThrows(OrderException.class, () -> service.statusOf("brian", order.getPublicId()));
        assertThrows(OrderException.class, () -> service.cancelOwn("brian", order.getPublicId()));
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
    }

    @Test
    void detailShowsTheSnapshotAndTheProgressSteps() {
        CustomerOrder order = storedOrder();
        OrderView view = service.detail("amina", order.getPublicId());

        assertEquals("KSh 130.00", view.getTotalText());
        assertEquals("Table 7", view.getTableLabel());
        assertEquals("Cash", view.getPaymentLabel());
        assertEquals(2, view.getLines().size());
        assertEquals("KSh 100.00", view.getLines().get(0).getLineTotalText());
        assertTrue(view.isCanCancel());
        assertFalse(view.isTerminal());
        assertEquals(List.of("Submitted:current", "Accepted:upcoming", "Preparing:upcoming", "Ready:upcoming",
                "Completed:upcoming"), stepStates(view));
        assertNotNull(view.getSteps().get(0).getTimeText());

        order.moveTo(OrderStatus.ACCEPTED, "staff1", null);
        order.moveTo(OrderStatus.PREPARING, "staff1", null);
        OrderView later = service.detail("amina", order.getPublicId());
        assertEquals(List.of("Submitted:done", "Accepted:done", "Preparing:current", "Ready:upcoming",
                "Completed:upcoming"), stepStates(later));
        assertFalse(later.isCanCancel());

        order.moveTo(OrderStatus.READY, "staff1", null);
        order.moveTo(OrderStatus.COMPLETED, "staff1", null);
        OrderView finished = service.detail("amina", order.getPublicId());
        assertEquals(List.of("Submitted:done", "Accepted:done", "Preparing:done", "Ready:done", "Completed:done"),
                stepStates(finished));
        assertTrue(finished.isTerminal());
    }

    private static List<String> stepStates(OrderView view) {
        return view.getSteps().stream().map(s -> s.getLabel() + ":" + s.getState()).toList();
    }

    @Test
    void historyListsTheStudentsOrdersWithAShortItemSummary() {
        CustomerOrder order = storedOrder();
        when(orders.findByStudent_UsernameOrderByCreatedAtDesc(eq("amina"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        Page<OrderSummary> page = service.history("amina", 0);

        OrderSummary summary = page.getContent().get(0);
        assertEquals("SC-ABC123", summary.getOrderNumber());
        assertEquals("2 x Tea, 1 x Samosa", summary.getItemSummary());
        assertEquals("KSh 130.00", summary.getTotalText());
        assertEquals("submitted", summary.getStatusClass());
        assertTrue(summary.isActive());
    }

    @Test
    void activeOrdersAskOnlyForStatusesThatAreStillInProgress() {
        CustomerOrder order = storedOrder();
        when(orders.findTop5ByStudent_UsernameAndStatusCodeInOrderByCreatedAtDesc(eq("amina"), anyCollection()))
                .thenReturn(List.of(order));

        List<OrderSummary> active = service.activeOrders("amina");

        assertEquals(1, active.size());
        verify(orders).findTop5ByStudent_UsernameAndStatusCodeInOrderByCreatedAtDesc(eq("amina"),
                argThat(statuses -> statuses.size() == 4 && statuses.contains("READY") && !statuses.contains("COMPLETED")
                        && !statuses.contains("CANCELLED")));
    }

    // ---- cancelling ----

    @Test
    void aStudentCanCancelBeforeAcceptanceAndItIsRecorded() {
        CustomerOrder order = storedOrder();
        service.cancelOwn("amina", order.getPublicId());

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(2, order.getHistory().size());
        assertEquals("Cancelled by the student", order.getHistory().get(1).getReason());
        verify(audit).record(eq("amina"), eq("ORDER_CANCELLED"), eq("ORDER"), eq(order.getPublicId()), eq("SUCCESS"),
                anyString());

        OrderView view = service.detail("amina", order.getPublicId());
        assertEquals(List.of("Submitted:done", "Cancelled:cancelled"), stepStates(view));
        assertTrue(view.isTerminal());
        assertFalse(view.isCanCancel());
    }

    @Test
    void aStudentCannotCancelOnceStaffHaveAccepted() {
        CustomerOrder order = storedOrder();
        order.moveTo(OrderStatus.ACCEPTED, "staff1", null);

        OrderException e = assertThrows(OrderException.class, () -> service.cancelOwn("amina", order.getPublicId()));
        assertTrue(e.getMessage().contains("can no longer be cancelled"));
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        verify(audit, never()).record(any(), eq("ORDER_CANCELLED"), any(), any(), any(), any());
    }

    @Test
    void anOrderCannotSkipStepsOrLeaveAFinishedState() {
        CustomerOrder order = storedOrder();
        assertThrows(IllegalStateException.class, () -> order.moveTo(OrderStatus.READY, "staff1", null));
        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
        assertEquals(1, order.getHistory().size());

        order.moveTo(OrderStatus.CANCELLED, "staff1", "Out of stock");
        assertThrows(IllegalStateException.class, () -> order.moveTo(OrderStatus.ACCEPTED, "staff1", null));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // ---- cancellation notes ----

    @Test
    void aStaffCancellationIsExplainedToTheStudent() {
        CustomerOrder order = storedOrder();
        order.moveTo(OrderStatus.CANCELLED, "sam", "Out of stock");
        assertEquals("Cancelled by staff: Out of stock", service.detail("amina", order.getPublicId()).getCancelNote());
    }

    @Test
    void aStudentsOwnCancellationIsSaidPlainly() {
        CustomerOrder order = storedOrder();
        service.cancelOwn("amina", order.getPublicId());
        assertEquals("You cancelled this order.", service.detail("amina", order.getPublicId()).getCancelNote());
    }

    @Test
    void ordersThatAreNotCancelledHaveNoNote() {
        CustomerOrder order = storedOrder();
        assertEquals(null, service.detail("amina", order.getPublicId()).getCancelNote());
        order.moveTo(OrderStatus.ACCEPTED, "sam", null);
        assertEquals(null, service.detail("amina", order.getPublicId()).getCancelNote());
    }

    // ---- payments follow the order ----

    @Test
    void placingAnOrderOpensItsPaymentRecordForTheSavedOrder() {
        service.place("amina", lines("tea", 2, "samosa", 1), "t1", PaymentMethod.CASH, "key-12345678");

        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        ArgumentCaptor<CustomerOrder> opened = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(payments).openFor(opened.capture());
        assertSame(saved.getValue(), opened.getValue());
    }

    @Test
    void anOrderThatCannotBePlacedOpensNoPayment() {
        menuHas(TEA, JUICE);
        assertThrows(OrderException.class,
                () -> service.place("amina", lines("tea", 1, "juice", 1), "t1", PaymentMethod.CASH, "key-12345678"));
        verify(payments, never()).openFor(any());
    }

    @Test
    void aRepeatedSubmitOpensNoSecondPayment() {
        CustomerOrder earlier = storedOrder();
        when(orders.findByStudent_UsernameAndIdempotencyKey("amina", "key-12345678")).thenReturn(Optional.of(earlier));

        service.place("amina", lines("tea", 1), "t1", PaymentMethod.CASH, "key-12345678");

        verify(payments, never()).openFor(any());
    }

    @Test
    void cancellingYourOwnOrderSettlesItsPaymentAsTheStudent() {
        CustomerOrder order = storedOrder();
        service.cancelOwn("amina", order.getPublicId());
        verify(payments).settleOnCancellation("amina", order);
    }

    @Test
    void aRefusedCancellationLeavesThePaymentAlone() {
        CustomerOrder order = storedOrder();
        order.moveTo(OrderStatus.ACCEPTED, "staff1", null);

        assertThrows(OrderException.class, () -> service.cancelOwn("amina", order.getPublicId()));
        assertThrows(OrderException.class, () -> service.cancelOwn("brian", order.getPublicId()));

        verify(payments, never()).settleOnCancellation(any(), any());
    }

    @Test
    void theOrderPageCarriesThePaymentStatusAndNote() {
        CustomerOrder order = storedOrder();
        PaymentView pending = new PaymentView("PENDING", "Awaiting payment", null, true, false, false,
                "Staff write down your payment when they receive it.");
        when(payments.viewFor(order)).thenReturn(pending);

        OrderView view = service.detail("amina", order.getPublicId());

        assertEquals("PENDING", view.getPaymentStatusCode());
        assertEquals("pending", view.getPaymentStatusClass());
        assertEquals("Awaiting payment", view.getPaymentStatusLabel());
        assertEquals("Staff write down your payment when they receive it.", view.getPaymentNote());
    }

    @Test
    void anOrderWithoutAPaymentRecordShowsNoPaymentStatus() {
        CustomerOrder order = storedOrder();

        OrderView view = service.detail("amina", order.getPublicId());

        assertNull(view.getPaymentStatusCode());
        assertNull(view.getPaymentStatusLabel());
        assertNull(view.getPaymentNote());
        assertEquals("", view.getPaymentStatusClass());
    }
}
