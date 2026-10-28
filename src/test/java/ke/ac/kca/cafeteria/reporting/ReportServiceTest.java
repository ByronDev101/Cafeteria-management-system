package ke.ac.kca.cafeteria.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.finance.PaymentService;
import ke.ac.kca.cafeteria.finance.PaymentView;
import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

class ReportServiceTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private ReportRepository reports;
    private PaymentService payments;
    private ReportService service;
    private AppUser amina;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        payments = mock(PaymentService.class);
        service = new ReportService(reports, payments, "Africa/Nairobi");
        amina = new AppUser("amina", "hash", "Amina Wanjiru");
        signInAs("ROLE_MANAGER");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("pat", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private CustomerOrder order(String number, long id) {
        CustomerOrder order = new CustomerOrder(number, amina, 5L, "Table 7", PaymentMethod.CASH, null);
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 2);
        order.addItem(2L, "Samosa", new BigDecimal("30.00"), 1);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private ManagerReport buildDefault() {
        return service.build(null, null, null, null, null);
    }

    // ---- who may see it ----

    @Test
    void onlyManagersCanBuildAReport() {
        for (String role : List.of("ROLE_STAFF", "ROLE_STUDENT", "ROLE_ADMIN")) {
            signInAs(role);
            assertThrows(AccessDeniedException.class, this::buildDefault, role);
        }
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, this::buildDefault);
        verify(reports, never()).orderTotalsByStatus(any(), any());
        verify(reports, never()).paymentTotals(any(), any());
    }

    // ---- orders by status ----

    @Test
    void ordersByStatusListEveryStatusEvenWhenNoneWerePlaced() {
        when(reports.orderTotalsByStatus(any(Instant.class), any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[] {"SUBMITTED", 2L, new BigDecimal("260.00")},
                new Object[] {"COMPLETED", 5L, new BigDecimal("900.00")}));

        ManagerReport report = buildDefault();

        List<ReportLine> lines = report.getOrderLines();
        assertEquals(6, lines.size());
        assertEquals("Submitted", lines.get(0).getLabel());
        assertEquals(2L, lines.get(0).getCount());
        assertEquals("KSh 260.00", lines.get(0).getAmountText());
        assertEquals("status-submitted", lines.get(0).getStyleClass());
        assertEquals(0L, lines.get(1).getCount());
        assertEquals("KSh 0.00", lines.get(1).getAmountText());
        assertEquals("Completed", lines.get(4).getLabel());
        assertEquals(5L, lines.get(4).getCount());
        assertEquals("Cancelled", lines.get(5).getLabel());
        assertEquals(7L, report.getOrderCount());
    }

    // ---- payments ----

    @Test
    void paymentsAreTotalledByStatusAndReceivedMoneyIsSplitByMethod() {
        when(reports.paymentTotals(any(Instant.class), any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[] {"VERIFIED", "CASH", 3L, new BigDecimal("300.00")},
                new Object[] {"VERIFIED", "MPESA_TILL", 2L, new BigDecimal("200.00")},
                new Object[] {"PENDING", "CASH", 1L, new BigDecimal("50.00")},
                new Object[] {"REFUND_DUE", "CASH", 1L, new BigDecimal("80.00")},
                new Object[] {"REFUNDED", "MPESA_TILL", 1L, new BigDecimal("60.00")},
                new Object[] {"NOT_A_STATUS", "CASH", 9L, new BigDecimal("1.00")}));

        ManagerReport report = buildDefault();

        List<ReportLine> lines = report.getPaymentLines();
        assertEquals(5, lines.size());
        assertEquals("Awaiting payment", lines.get(0).getLabel());
        assertEquals(1L, lines.get(0).getCount());
        assertEquals("KSh 50.00", lines.get(0).getAmountText());
        assertEquals("Payment received", lines.get(1).getLabel());
        assertEquals(5L, lines.get(1).getCount());
        assertEquals("KSh 500.00", lines.get(1).getAmountText());
        assertEquals("pay-verified", lines.get(1).getStyleClass());
        assertEquals(0L, lines.get(2).getCount());
        assertEquals("KSh 0.00", lines.get(2).getAmountText());
        assertEquals("pay-refund-due", lines.get(3).getStyleClass());
        assertEquals("KSh 80.00", lines.get(3).getAmountText());
        assertEquals("KSh 60.00", lines.get(4).getAmountText());

        List<ReportLine> methods = report.getMethodLines();
        assertEquals(2, methods.size());
        assertEquals("Cash", methods.get(0).getLabel());
        assertEquals(3L, methods.get(0).getCount());
        assertEquals("KSh 300.00", methods.get(0).getAmountText());
        assertEquals("M-Pesa till", methods.get(1).getLabel());
        assertEquals("KSh 200.00", methods.get(1).getAmountText());
    }

    @Test
    void creditAppearsByMethodOnlyOnceSomeCreditWasReceived() {
        assertEquals(2, buildDefault().getMethodLines().size());

        when(reports.paymentTotals(any(Instant.class), any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[] {"VERIFIED", "CREDIT", 1L, new BigDecimal("40.00")}));
        List<ReportLine> methods = buildDefault().getMethodLines();
        assertEquals(3, methods.size());
        assertEquals("Cafeteria credit", methods.get(2).getLabel());
        assertEquals("KSh 40.00", methods.get(2).getAmountText());
    }

    // ---- range ----

    @Test
    void theDefaultRangeIsTheLastSevenDaysInCampusTime() {
        ManagerReport report = buildDefault();
        LocalDate today = LocalDate.now(NAIROBI);

        assertEquals(today.toString(), report.getToText());
        assertEquals(today.minusDays(6).toString(), report.getFromText());
        assertTrue(report.getWarnings().isEmpty());
    }

    @Test
    void aBadRangeFallsBackAndTheWarningIsShown() {
        ManagerReport report = service.build("2026-10-20", "2026-10-10", null, null, null);

        assertEquals(LocalDate.now(NAIROBI).toString(), report.getToText());
        assertEquals(1, report.getWarnings().size());
        assertTrue(report.getWarnings().get(0).contains("start date was after the end date"));
    }

    // ---- search ----

    @SuppressWarnings("unchecked")
    private Collection<String> capturedStatuses() {
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(reports).search(any(Instant.class), any(Instant.class), statuses.capture(), any(), any(), any(Pageable.class));
        return statuses.getValue();
    }

    @Test
    void theSearchIsLimitedToOneHundredAndSaysWhenThereAreMore() {
        List<CustomerOrder> found = new ArrayList<>();
        for (int i = 0; i < ReportService.SEARCH_LIMIT + 1; i++) {
            found.add(order("SC-" + String.format("%06d", i), 100L + i));
        }
        when(reports.search(any(Instant.class), any(Instant.class), any(), any(), any(), any(Pageable.class)))
                .thenReturn(found);

        ManagerReport report = buildDefault();

        assertEquals(100, report.getRows().size());
        assertTrue(report.isMoreMatches());
        assertEquals(100, report.getLimit());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(reports).search(any(Instant.class), any(Instant.class), any(), any(), any(), page.capture());
        assertEquals(101, page.getValue().getPageSize());
    }

    @Test
    void fewerMatchesThanTheLimitSaysNothingAboutMore() {
        when(reports.search(any(Instant.class), any(Instant.class), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(order("SC-ABC123", 11L)));

        ManagerReport report = buildDefault();

        assertEquals(1, report.getRows().size());
        assertFalse(report.isMoreMatches());
    }

    @Test
    void aStatusFilterNarrowsTheSearchAndAnUnknownOneIsIgnoredWithAWarning() {
        ManagerReport cancelled = service.build(null, null, "CANCELLED", null, null);
        assertEquals(List.of("CANCELLED"), List.copyOf(capturedStatuses()));
        assertEquals("CANCELLED", cancelled.getStatus());
        assertTrue(cancelled.getWarnings().isEmpty());
    }

    @Test
    void anUnknownStatusShowsAllStatusesWithAWarning() {
        ManagerReport report = service.build(null, null, "BOGUS' OR 1=1", null, null);

        assertEquals(6, capturedStatuses().size());
        assertEquals("", report.getStatus());
        assertEquals(1, report.getWarnings().size());
        assertTrue(report.getWarnings().get(0).contains("not recognised"));
    }

    @Test
    void searchTextIsCleanedSoItCannotActAsAWildcard() {
        ManagerReport report = service.build(null, null, null, " SC-%_ ", "Table\\7");

        ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> table = ArgumentCaptor.forClass(String.class);
        verify(reports).search(any(Instant.class), any(Instant.class), any(), number.capture(), table.capture(),
                any(Pageable.class));
        assertEquals("%sc-%", number.getValue());
        assertEquals("%table 7%", table.getValue());
        assertEquals("SC-", report.getNumberText());
        assertEquals("Table 7", report.getTableText());
    }

    @Test
    void noSearchTextMatchesEverything() {
        service.build(null, null, null, null, "   ");

        ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> table = ArgumentCaptor.forClass(String.class);
        verify(reports).search(any(Instant.class), any(Instant.class), any(), number.capture(), table.capture(),
                any(Pageable.class));
        assertEquals("%%", number.getValue());
        assertEquals("%%", table.getValue());
    }

    @Test
    void cleanTextCapsTheLengthAndDropsControlCharacters() {
        assertEquals("", ReportService.cleanText(null));
        assertEquals("AB CD", ReportService.cleanText("AB\n\tCD"));
        assertEquals(40, ReportService.cleanText("x".repeat(100)).length());
    }

    // ---- rows ----

    @Test
    void eachRowShowsTheOrderAndItsPaymentButNotWhoOrderedIt() {
        CustomerOrder order = order("SC-ABC123", 11L);
        when(reports.search(any(Instant.class), any(Instant.class), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(order));
        PaymentView pending = new PaymentView("PENDING", "Awaiting payment", null, true, false, false, "note");
        Map<Long, PaymentView> views = new HashMap<>();
        views.put(11L, pending);
        when(payments.viewsFor(any())).thenReturn(views);

        ReportRow row = buildDefault().getRows().get(0);

        assertEquals("SC-ABC123", row.getOrderNumber());
        assertEquals("Table 7", row.getTableLabel());
        assertEquals("Submitted", row.getStatusLabel());
        assertEquals("status-submitted", row.getStatusClass());
        assertEquals("KSh 130.00", row.getTotalText());
        assertEquals("Cash", row.getMethodLabel());
        assertEquals("Awaiting payment", row.getPaymentLabel());
        assertEquals("pay-pending", row.getPaymentClass());
        assertFalse(row.getCreatedText().isBlank());

        for (Method method : ReportRow.class.getMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("student") || name.contains("user") || name.contains("email")
                            || name.contains("account") || name.contains("password"),
                    "the report never shows who ordered: " + method.getName());
        }
    }

    @Test
    void anOrderWithNoPaymentRecordHasNoPaymentLabel() {
        when(reports.search(any(Instant.class), any(Instant.class), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(order("SC-ABC123", 11L)));

        ReportRow row = buildDefault().getRows().get(0);

        assertNull(row.getPaymentLabel());
        assertEquals("", row.getPaymentClass());
    }
}
