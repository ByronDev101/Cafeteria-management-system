package ke.ac.kca.cafeteria.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import ke.ac.kca.cafeteria.identity.Role;
import ke.ac.kca.cafeteria.identity.UserRepository;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.reporting.AuditService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

class CreditServiceTest {

    private CreditAccountRepository accounts;
    private CreditEntryRepository entries;
    private UserRepository users;
    private AuditService audit;
    private CreditService service;
    private AppUser amina;

    @BeforeEach
    void setUp() {
        accounts = mock(CreditAccountRepository.class);
        entries = mock(CreditEntryRepository.class);
        users = mock(UserRepository.class);
        audit = mock(AuditService.class);
        service = new CreditService(accounts, entries, users, audit, "Africa/Nairobi");
        amina = new AppUser("amina", "hash", "Amina Wanjiru");
        amina.addRole(new Role("STUDENT"));
        ReflectionTestUtils.setField(amina, "id", 5L);
        when(users.findByPublicId(amina.getPublicId())).thenReturn(Optional.of(amina));
        when(users.findByUsername("amina")).thenReturn(Optional.of(amina));
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

    /** An order of KSh 130.00 (2 x 50 and 1 x 30) with a database id, paid the given way. */
    private CustomerOrder order(PaymentMethod method) {
        CustomerOrder order = new CustomerOrder("SC-ABC123", amina, 5L, "Table 7", method, "key-12345678");
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 2);
        order.addItem(2L, "Samosa", new BigDecimal("30.00"), 1);
        ReflectionTestUtils.setField(order, "id", 11L);
        return order;
    }

    /** Ledger rows as the repository returns them: type, amount, type, amount ... */
    private static List<Object[]> rows(String... typeAndAmount) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < typeAndAmount.length; i += 2) {
            rows.add(new Object[] {typeAndAmount[i], new BigDecimal(typeAndAmount[i + 1])});
        }
        return rows;
    }

    private CreditAccount accountWithLimit(String limit) {
        CreditAccount account = new CreditAccount(amina, new BigDecimal(limit), "pat");
        when(accounts.findForUpdate(5L)).thenReturn(Optional.of(account));
        when(accounts.findByStudent_Id(5L)).thenReturn(Optional.of(account));
        return account;
    }

    private ArgumentCaptor<CreditEntry> savedEntry() {
        ArgumentCaptor<CreditEntry> saved = ArgumentCaptor.forClass(CreditEntry.class);
        verify(entries).save(saved.capture());
        return saved;
    }

    // ---- holding credit when an order is placed ----

    @Test
    void anOrderThatFitsUnderTheLimitPutsItsTotalOnHoldWithTheAccountLocked() {
        accountWithLimit("500.00");
        // held 100 (250 reserved - 150 posted), owed 150: 250 used, 250 available
        when(entries.totalsForStudent(5L)).thenReturn(rows("RESERVE", "250.00", "POST", "150.00"));
        CustomerOrder order = order(PaymentMethod.CREDIT);

        service.reserve(order);

        CreditEntry entry = savedEntry().getValue();
        assertEquals(CreditEntryType.RESERVE, entry.getType());
        assertEquals(new BigDecimal("130.00"), entry.getAmount());
        assertEquals("amina", entry.getActor());
        assertSame(order, entry.getOrder());
        assertSame(amina, entry.getStudent());
        verify(accounts).findForUpdate(5L);
        verify(audit).record(eq("amina"), eq("CREDIT_RESERVED"), eq("CREDIT"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("SC-ABC123") && details.contains("130.00")
                        && details.contains("available after=120.00") && details.contains("no real money")));
    }

    @Test
    void exactlyTheAvailableAmountIsAllowed() {
        accountWithLimit("130.00");
        service.reserve(order(PaymentMethod.CREDIT));
        assertEquals(new BigDecimal("130.00"), savedEntry().getValue().getAmount());
    }

    @Test
    void anOrderOverWhatIsAvailableIsRefusedWithTheFiguresAndNothingIsHeld() {
        accountWithLimit("300.00");
        // held 100.00 (250 reserved - 150 posted), owed 149.99 (150 posted - 0.01 paid): 50.01 left of 300
        when(entries.totalsForStudent(5L)).thenReturn(rows("RESERVE", "250.00", "POST", "150.00", "SETTLEMENT", "0.01"));

        OrderException e = assertThrows(OrderException.class, () -> service.reserve(order(PaymentMethod.CREDIT)));

        assertTrue(e.getMessage().contains("KSh 130.00"), e.getMessage());
        assertTrue(e.getMessage().contains("KSh 50.01"), e.getMessage());
        verify(entries, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aStudentWithNoCreditAccountIsRefused() {
        OrderException e = assertThrows(OrderException.class, () -> service.reserve(order(PaymentMethod.CREDIT)));
        assertTrue(e.getMessage().contains("do not have cafeteria credit"));
        verify(entries, never()).save(any());
    }

    @Test
    void aLimitOfZeroMeansNothingCanBeHeld() {
        accountWithLimit("0.00");
        OrderException e = assertThrows(OrderException.class, () -> service.reserve(order(PaymentMethod.CREDIT)));
        assertTrue(e.getMessage().contains("KSh 0.00"));
    }

    @Test
    void anOrderAlreadyOnHoldIsNotHeldTwice() {
        accountWithLimit("500.00");
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00"));

        service.reserve(order(PaymentMethod.CREDIT));

        verify(entries, never()).save(any());
    }

    @Test
    void ordersPaidAnotherWayNeverTouchTheLedger() {
        service.reserve(order(PaymentMethod.CASH));
        service.reserve(order(PaymentMethod.MPESA_TILL));
        verify(accounts, never()).findForUpdate(any());
        verify(entries, never()).save(any());
    }

    // ---- charging when staff accept ----

    @Test
    void acceptingAnOrderTurnsItsHoldIntoAChargeAsTheStaffMember() {
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00"));
        CustomerOrder order = order(PaymentMethod.CREDIT);

        service.postOnAcceptance("sam", order);

        CreditEntry entry = savedEntry().getValue();
        assertEquals(CreditEntryType.POST, entry.getType());
        assertEquals(new BigDecimal("130.00"), entry.getAmount());
        assertEquals("sam", entry.getActor());
        verify(audit).record(eq("sam"), eq("CREDIT_POSTED"), eq("CREDIT"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("130.00") && details.contains("hold -> charge")));
    }

    @Test
    void acceptingTwiceChargesOnlyOnce() {
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00", "POST", "130.00"));
        service.postOnAcceptance("sam", order(PaymentMethod.CREDIT));
        verify(entries, never()).save(any());
    }

    @Test
    void acceptingAnOrderWithNoCreditHeldIsRefused() {
        OrderException e = assertThrows(OrderException.class,
                () -> service.postOnAcceptance("sam", order(PaymentMethod.CREDIT)));
        assertTrue(e.getMessage().contains("No credit is held for order SC-ABC123"));
        verify(entries, never()).save(any());
    }

    @Test
    void acceptingAnOrderWhoseHoldWasReleasedIsRefused() {
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00", "RELEASE", "130.00"));
        assertThrows(OrderException.class, () -> service.postOnAcceptance("sam", order(PaymentMethod.CREDIT)));
    }

    @Test
    void acceptingACashOrderDoesNothing() {
        service.postOnAcceptance("sam", order(PaymentMethod.CASH));
        verify(entries, never()).save(any());
        verify(entries, never()).totalsForOrder(any());
    }

    // ---- giving credit back when an order is cancelled ----

    @Test
    void cancellingBeforeAcceptanceReleasesTheHold() {
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00"));
        CustomerOrder order = order(PaymentMethod.CREDIT);

        service.releaseOnCancellation("amina", order);

        CreditEntry entry = savedEntry().getValue();
        assertEquals(CreditEntryType.RELEASE, entry.getType());
        assertEquals(new BigDecimal("130.00"), entry.getAmount());
        verify(audit).record(eq("amina"), eq("CREDIT_RELEASED"), eq("CREDIT"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("hold released")));
    }

    @Test
    void cancellingAfterAcceptanceReversesTheCharge() {
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00", "POST", "130.00"));
        CustomerOrder order = order(PaymentMethod.CREDIT);

        service.releaseOnCancellation("sam", order);

        CreditEntry entry = savedEntry().getValue();
        assertEquals(CreditEntryType.REVERSAL, entry.getType());
        assertEquals(new BigDecimal("130.00"), entry.getAmount());
        assertEquals("sam", entry.getActor());
        verify(audit).record(eq("sam"), eq("CREDIT_REVERSED"), eq("CREDIT"), eq(order.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("charge reversed")));
    }

    @Test
    void cancellingTwiceDoesNothingTheSecondTime() {
        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00", "RELEASE", "130.00"));
        service.releaseOnCancellation("sam", order(PaymentMethod.CREDIT));

        when(entries.totalsForOrder(11L)).thenReturn(rows("RESERVE", "130.00", "POST", "130.00", "REVERSAL", "130.00"));
        service.releaseOnCancellation("sam", order(PaymentMethod.CREDIT));

        verify(entries, never()).save(any());
    }

    @Test
    void cancellingAnOrderPaidAnotherWayDoesNothing() {
        service.releaseOnCancellation("sam", order(PaymentMethod.CASH));
        verify(entries, never()).save(any());
        verify(entries, never()).totalsForOrder(any());
    }

    // ---- what a student sees ----

    @Test
    void aStudentWithNoAccountHasNothingAvailable() {
        CreditBalance balance = service.balanceOf("amina");

        assertFalse(balance.isHasAccount());
        assertFalse(balance.isCanUse());
        assertEquals("KSh 0.00", balance.getLimitText());
    }

    @Test
    void aStudentsFiguresComeFromTheLedger() {
        accountWithLimit("500.00");
        when(entries.totalsForStudent(5L)).thenReturn(rows("RESERVE", "250.00", "POST", "150.00", "SETTLEMENT", "50.00"));

        CreditBalance balance = service.balanceOf(" Amina ");

        assertTrue(balance.isHasAccount());
        assertEquals("KSh 500.00", balance.getLimitText());
        assertEquals("KSh 100.00", balance.getHeldText());   // 250 reserved - 150 posted
        assertEquals("KSh 100.00", balance.getOwedText());   // 150 posted - 50 paid
        assertEquals("KSh 300.00", balance.getAvailableText());
        assertTrue(balance.isCanUse());
    }

    @Test
    void anUnknownUsernameHasNoCredit() {
        assertFalse(service.balanceOf("nobody").isHasAccount());
    }

    @Test
    void recentLinesAreShownWithTheirOrderAndNote() {
        CustomerOrder order = order(PaymentMethod.CREDIT);
        CreditEntry hold = new CreditEntry(amina, order, CreditEntryType.RESERVE, new BigDecimal("130.00"), "amina", null);
        CreditEntry payment = new CreditEntry(amina, null, CreditEntryType.SETTLEMENT, new BigDecimal("50.00"), "pat",
                "Paid at the till");
        when(entries.recentForStudent(eq("amina"), any(Pageable.class))).thenReturn(List.of(payment, hold));

        List<CreditEntryView> views = service.recentEntries("amina");

        assertEquals(2, views.size());
        assertEquals("Payment recorded", views.get(0).getTypeLabel());
        assertEquals("settlement", views.get(0).getTypeClass());
        assertEquals("KSh 50.00", views.get(0).getAmountText());
        assertNull(views.get(0).getOrderNumber());
        assertEquals("Paid at the till", views.get(0).getNote());
        assertEquals("Held for an order", views.get(1).getTypeLabel());
        assertEquals("SC-ABC123", views.get(1).getOrderNumber());
        assertFalse(views.get(1).getCreatedText().isBlank());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(entries).recentForStudent(eq("amina"), page.capture());
        assertEquals(CreditService.RECENT_ENTRIES, page.getValue().getPageSize());
    }

    // ---- who may do this ----

    @Test
    void onlyManagersMaySeeOrChangeCredit() {
        for (String role : List.of("ROLE_STAFF", "ROLE_STUDENT", "ROLE_ADMIN")) {
            signInAs(role);
            assertThrows(AccessDeniedException.class, () -> service.overview(null), role);
            assertThrows(AccessDeniedException.class, () -> service.setLimit("x", amina.getPublicId(), "500"), role);
            assertThrows(AccessDeniedException.class, () -> service.settle("x", amina.getPublicId(), "10", null), role);
        }
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.setLimit("x", amina.getPublicId(), "500"));
        verify(accounts, never()).save(any());
        verify(entries, never()).save(any());
    }

    // ---- setting a limit ----

    @Test
    void theFirstLimitCreatesTheAccount() {
        CreditRow row = service.setLimit("pat", amina.getPublicId(), " 500 ");

        ArgumentCaptor<CreditAccount> saved = ArgumentCaptor.forClass(CreditAccount.class);
        verify(accounts).save(saved.capture());
        assertEquals(new BigDecimal("500.00"), saved.getValue().getCreditLimit());
        assertEquals("pat", saved.getValue().getUpdatedBy());
        assertSame(amina, saved.getValue().getStudent());
        assertEquals("KSh 500.00", row.getBalance().getLimitText());
        assertEquals("Amina Wanjiru", row.getDisplayName());
        verify(audit).record(eq("pat"), eq("CREDIT_LIMIT_SET"), eq("CREDIT"), eq(amina.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("student=amina") && details.contains("limit 0 -> 500.00")));
    }

    @Test
    void aLaterLimitChangesTheExistingAccountAndSaysWhatItWas() {
        CreditAccount account = accountWithLimit("200.00");

        service.setLimit("pat", amina.getPublicId(), "800.50");

        assertEquals(new BigDecimal("800.50"), account.getCreditLimit());
        assertEquals("pat", account.getUpdatedBy());
        verify(accounts, never()).save(any());
        verify(audit).record(eq("pat"), eq("CREDIT_LIMIT_SET"), eq("CREDIT"), eq(amina.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("limit 200.00 -> 800.50")));
    }

    @Test
    void aLimitOfZeroAndTheMostAllowedBothWork() {
        CreditAccount account = accountWithLimit("200.00");
        service.setLimit("pat", amina.getPublicId(), "0");
        assertEquals(0, account.getCreditLimit().signum());
        service.setLimit("pat", amina.getPublicId(), "50000");
        assertEquals(new BigDecimal("50000.00"), account.getCreditLimit());
    }

    @Test
    void limitsThatAreNotSensibleAmountsAreRefusedAndNothingChanges() {
        CreditAccount account = accountWithLimit("200.00");
        for (String bad : new String[] {null, "", "  ", "abc", "-5", "12.345", "1e3", "5,000", "' OR 1=1 --"}) {
            OrderException e = assertThrows(OrderException.class,
                    () -> service.setLimit("pat", amina.getPublicId(), bad), String.valueOf(bad));
            assertTrue(e.getMessage().startsWith("Enter the amount in KSh"), e.getMessage());
        }
        OrderException tooMuch = assertThrows(OrderException.class,
                () -> service.setLimit("pat", amina.getPublicId(), "50000.01"));
        assertTrue(tooMuch.getMessage().contains("at most KSh 50,000.00"));
        assertEquals(new BigDecimal("200.00"), account.getCreditLimit());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void onlyActiveStudentsCanBeGivenCredit() {
        assertEquals("That student was not found.",
                assertThrows(OrderException.class, () -> service.setLimit("pat", "no-such-id", "100")).getMessage());
        assertEquals("That student was not found.",
                assertThrows(OrderException.class, () -> service.setLimit("pat", null, "100")).getMessage());

        AppUser staff = new AppUser("sam", "hash", "Sam Otieno");
        staff.addRole(new Role("STAFF"));
        when(users.findByPublicId(staff.getPublicId())).thenReturn(Optional.of(staff));
        assertEquals("That student was not found.",
                assertThrows(OrderException.class, () -> service.setLimit("pat", staff.getPublicId(), "100")).getMessage());

        amina.setActive(false);
        OrderException inactive = assertThrows(OrderException.class,
                () -> service.setLimit("pat", amina.getPublicId(), "100"));
        assertTrue(inactive.getMessage().contains("not active"));
        verify(accounts, never()).save(any());
    }

    // ---- recording a payment ----

    @Test
    void aPaymentIsAddedToTheLedgerAsTheManagerAndAudited() {
        accountWithLimit("500.00");
        when(entries.totalsForStudent(5L)).thenReturn(rows("RESERVE", "150.00", "POST", "150.00"));

        service.settle("pat", amina.getPublicId(), "100", "  Paid   at the till ");

        CreditEntry entry = savedEntry().getValue();
        assertEquals(CreditEntryType.SETTLEMENT, entry.getType());
        assertEquals(new BigDecimal("100.00"), entry.getAmount());
        assertEquals("pat", entry.getActor());
        assertEquals("Paid at the till", entry.getNote());
        assertNull(entry.getOrder());
        verify(audit).record(eq("pat"), eq("CREDIT_SETTLED"), eq("CREDIT"), eq(amina.getPublicId()), eq("SUCCESS"),
                argThat(details -> details.contains("owed 150.00 -> 50.00") && details.contains("note=Paid at the till")
                        && details.contains("no real money")));
    }

    @Test
    void aStudentCanPayOffExactlyWhatTheyOwe() {
        accountWithLimit("500.00");
        when(entries.totalsForStudent(5L)).thenReturn(rows("RESERVE", "150.00", "POST", "150.00"));
        service.settle("pat", amina.getPublicId(), "150.00", null);
        assertEquals(new BigDecimal("150.00"), savedEntry().getValue().getAmount());
        assertNull(savedEntry().getValue().getNote());
    }

    @Test
    void aPaymentCannotBeMoreThanWhatIsOwed() {
        accountWithLimit("500.00");
        when(entries.totalsForStudent(5L)).thenReturn(rows("RESERVE", "150.00", "POST", "150.00"));

        OrderException e = assertThrows(OrderException.class,
                () -> service.settle("pat", amina.getPublicId(), "150.01", null));

        assertTrue(e.getMessage().contains("owes KSh 150.00"), e.getMessage());
        assertTrue(e.getMessage().contains("KSh 150.01"), e.getMessage());
        verify(entries, never()).save(any());
    }

    @Test
    void aStudentWhoOwesNothingCannotHaveAPaymentRecorded() {
        accountWithLimit("500.00");
        OrderException e = assertThrows(OrderException.class,
                () -> service.settle("pat", amina.getPublicId(), "10", null));
        assertTrue(e.getMessage().contains("owes KSh 0.00"));
    }

    @Test
    void aPaymentMustBeANumberAboveZeroAndAStudentNeedsAnAccount() {
        accountWithLimit("500.00");
        assertTrue(assertThrows(OrderException.class, () -> service.settle("pat", amina.getPublicId(), "0", null))
                .getMessage().contains("above zero"));
        assertTrue(assertThrows(OrderException.class, () -> service.settle("pat", amina.getPublicId(), "ten", null))
                .getMessage().startsWith("Enter the amount in KSh"));
        verify(entries, never()).save(any());
    }

    @Test
    void aPaymentForAStudentWithNoAccountIsRefused() {
        OrderException e = assertThrows(OrderException.class,
                () -> service.settle("pat", amina.getPublicId(), "10", null));
        assertTrue(e.getMessage().contains("has no credit account"));
    }

    @Test
    void notesAreCleanedAndCapped() {
        assertNull(CreditService.cleanNote(null));
        assertNull(CreditService.cleanNote(" \n\t "));
        assertEquals("a b", CreditService.cleanNote("a\n\tb"));
        assertEquals(100, CreditService.cleanNote("x".repeat(100)).length());
        assertTrue(assertThrows(OrderException.class, () -> CreditService.cleanNote("x".repeat(101)))
                .getMessage().contains("at most 100"));
    }

    // ---- the manager's list ----

    @Test
    void withNoSearchTheListIsStudentsWhoAlreadyHaveCredit() {
        CreditAccount account = new CreditAccount(amina, new BigDecimal("500.00"), "pat");
        when(accounts.studentsWithAccounts(any(Pageable.class))).thenReturn(List.of(amina));
        when(accounts.findByStudentIds(any())).thenReturn(List.of(account));
        List<Object[]> totals = new ArrayList<>();
        totals.add(new Object[] {5L, "RESERVE", new BigDecimal("150.00")});
        totals.add(new Object[] {5L, "POST", new BigDecimal("150.00")});
        when(entries.totalsForStudents(any())).thenReturn(totals);

        List<CreditRow> rows = service.overview(null);

        assertEquals(1, rows.size());
        CreditRow row = rows.get(0);
        assertEquals("Amina Wanjiru", row.getDisplayName());
        assertEquals("amina", row.getUsername());
        assertEquals(amina.getPublicId(), row.getStudentPublicId());
        assertEquals("KSh 500.00", row.getBalance().getLimitText());
        assertEquals("KSh 150.00", row.getBalance().getOwedText());
        assertEquals("KSh 350.00", row.getBalance().getAvailableText());
        assertEquals("500.00", row.getLimitInput());
        assertTrue(row.isCanSettle());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(accounts).studentsWithAccounts(page.capture());
        assertEquals(CreditService.OVERVIEW_ACCOUNTS, page.getValue().getPageSize());
        verify(accounts, never()).searchStudents(any(), any());
    }

    @Test
    void aSearchFindsActiveStudentsAndCannotActAsAWildcard() {
        when(accounts.searchStudents(any(), any(Pageable.class))).thenReturn(List.of(amina));

        List<CreditRow> rows = service.overview(" Am%i_a ");

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(accounts).searchStudents(pattern.capture(), page.capture());
        assertEquals("%am i a%", pattern.getValue());
        assertEquals(CreditService.OVERVIEW_SEARCH, page.getValue().getPageSize());
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).getBalance().isHasAccount());
        assertEquals("KSh 0.00", rows.get(0).getBalance().getLimitText());
        assertFalse(rows.get(0).isCanSettle());
        verify(accounts, never()).studentsWithAccounts(any());
    }

    @Test
    void anEmptyAnswerSkipsTheOtherQueries() {
        assertTrue(service.overview("nobody").isEmpty());
        verify(accounts, never()).findByStudentIds(any());
        verify(entries, never()).totalsForStudents(any());
    }

    @Test
    void searchTextIsCappedAtFortyCharactersAndStripsControlCharacters() {
        assertEquals("", CreditService.cleanQuery(null));
        assertEquals("a b", CreditService.cleanQuery("a\n\tb"));
        assertEquals(40, CreditService.cleanQuery("x".repeat(100)).length());
    }
}
