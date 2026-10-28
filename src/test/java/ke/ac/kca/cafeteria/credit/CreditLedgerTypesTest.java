package ke.ac.kca.cafeteria.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.identity.AppUser;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The ledger line and account rules that need no database. */
class CreditLedgerTypesTest {

    private final AppUser amina = new AppUser("amina", "hash", "Amina Wanjiru");

    @Test
    void everyLineTypeHasALabelAndCanBeParsed() {
        for (CreditEntryType type : CreditEntryType.values()) {
            assertFalse(type.getLabel().isBlank());
            assertEquals(Optional.of(type), CreditEntryType.parse(" " + type.name() + " "));
        }
        assertTrue(CreditEntryType.parse("reserve").isEmpty());
        assertTrue(CreditEntryType.parse("' OR 1=1 --").isEmpty());
        assertTrue(CreditEntryType.parse(null).isEmpty());
    }

    @Test
    void aLedgerLineKeepsWhatItWasGiven() {
        CreditEntry entry = new CreditEntry(amina, null, CreditEntryType.SETTLEMENT, new BigDecimal("100.00"), "pat",
                "Paid at the till");

        assertEquals(CreditEntryType.SETTLEMENT, entry.getType());
        assertEquals(new BigDecimal("100.00"), entry.getAmount());
        assertEquals("pat", entry.getActor());
        assertEquals("Paid at the till", entry.getNote());
        assertSame(amina, entry.getStudent());
        assertNull(entry.getOrder());
        assertFalse(entry.getPublicId().isBlank());
        assertTrue(entry.getCreatedAt() != null);
    }

    @Test
    void aLedgerLineMustBeForAnAmountAboveZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreditEntry(amina, null, CreditEntryType.POST, BigDecimal.ZERO, "sam", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CreditEntry(amina, null, CreditEntryType.POST, new BigDecimal("-1.00"), "sam", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CreditEntry(amina, null, CreditEntryType.POST, null, "sam", null));
    }

    @Test
    void aLedgerLineCannotBeChangedOnceWritten() {
        for (Method method : CreditEntry.class.getMethods()) {
            assertFalse(method.getName().startsWith("set"), "no setters on the ledger: " + method.getName());
        }
        for (Method method : CreditEntryRepository.class.getMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.startsWith("delete") || name.startsWith("remove") || name.startsWith("update"),
                    "the ledger offers no delete or update: " + method.getName());
        }
    }

    @Test
    void anAccountKeepsItsLimitAndWhoChangedIt() {
        CreditAccount account = new CreditAccount(amina, new BigDecimal("200.00"), "pat");
        assertEquals(new BigDecimal("200.00"), account.getCreditLimit());
        assertEquals("pat", account.getUpdatedBy());

        account.changeLimit(new BigDecimal("800.00"), "lee");

        assertEquals(new BigDecimal("800.00"), account.getCreditLimit());
        assertEquals("lee", account.getUpdatedBy());
    }

    @Test
    void anAccountsLimitCanBeZeroButNeverNegative() {
        CreditAccount account = new CreditAccount(amina, BigDecimal.ZERO, "pat");
        assertEquals(0, account.getCreditLimit().signum());

        assertThrows(IllegalArgumentException.class, () -> account.changeLimit(new BigDecimal("-0.01"), "pat"));
        assertThrows(IllegalArgumentException.class, () -> account.changeLimit(null, "pat"));
        assertThrows(IllegalArgumentException.class, () -> new CreditAccount(amina, new BigDecimal("-5"), "pat"));
        assertEquals(0, account.getCreditLimit().signum());
    }
}
