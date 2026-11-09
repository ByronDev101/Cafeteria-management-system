package ke.ac.kca.cafeteria.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.PlacedOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** The migrations on a real MySQL, and the database's own rules, which hold even if the application is bypassed. */
class SchemaAndConstraintsIT extends DatabaseIT {

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private void insertPayment(long orderId, String amount) {
        jdbc.update("insert into payment_records (public_id, order_id, method_code, amount, status, prototype, version, "
                        + "created_at, updated_at) values (?, ?, 'CASH', ?, 'PENDING', true, 0, ?, ?)",
                UUID.randomUUID().toString(), orderId, new BigDecimal(amount), now(), now());
    }

    private void insertEntry(long studentId, String type, String amount) {
        jdbc.update("insert into credit_entries (public_id, student_id, entry_type, amount, actor, created_at) "
                        + "values (?, ?, ?, ?, 'pat', ?)",
                UUID.randomUUID().toString(), studentId, type, new BigDecimal(amount), now());
    }

    private void insertAccount(long studentId, String limit) {
        jdbc.update("insert into credit_accounts (student_id, credit_limit, updated_by, version, created_at, updated_at) "
                + "values (?, ?, 'pat', 0, ?, ?)", studentId, new BigDecimal(limit), now(), now());
    }

    @Test
    void everyMigrationWasAppliedAndTheNewTablesExist() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version is not null", Integer.class);
        Integer latest = jdbc.queryForObject(
                "select max(cast(version as unsigned)) from flyway_schema_history", Integer.class);
        Integer tables = jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = database() and table_name in ('payment_records', 'credit_accounts', 'credit_entries')",
                Integer.class);

        assertTrue(applied != null && applied >= 9, "applied migrations: " + applied);
        assertTrue(latest != null && latest >= 9, "latest version: " + latest);
        assertEquals(3, tables);
    }

    @Test
    void anOrderCanOnlyEverHaveOnePaymentRecord() {
        student("amina");
        PlacedOrder placed = place("amina", PaymentMethod.CASH, 1);
        long orderId = jdbc.queryForObject("select id from orders where public_id = ?", Long.class, placed.publicId());

        assertEquals(1, count("payment_records"));
        assertThrows(DataAccessException.class, () -> insertPayment(orderId, "10.00"));
        assertEquals(1, count("payment_records"));
    }

    @Test
    void aPaymentAmountCannotBeNegative() {
        student("amina");
        PlacedOrder placed = place("amina", PaymentMethod.CASH, 1);
        long orderId = jdbc.queryForObject("select id from orders where public_id = ?", Long.class, placed.publicId());
        jdbc.update("delete from payment_records where order_id = ?", orderId);

        insertPayment(orderId, "10.00");                       // a valid one is accepted, so the next failure is the rule
        assertEquals(1, count("payment_records"));
        jdbc.update("delete from payment_records where order_id = ?", orderId);

        assertThrows(DataAccessException.class, () -> insertPayment(orderId, "-1.00"));
        assertEquals(0, count("payment_records"));
    }

    @Test
    void aLedgerLineMustBeAboveZeroAndOfAKnownType() {
        AppUser amina = student("amina");

        insertEntry(amina.getId(), "RESERVE", "10.00");        // valid control
        assertEquals(1, count("credit_entries"));
        assertThrows(DataAccessException.class, () -> insertEntry(amina.getId(), "RESERVE", "0.00"));
        assertThrows(DataAccessException.class, () -> insertEntry(amina.getId(), "RESERVE", "-5.00"));
        assertThrows(DataAccessException.class, () -> insertEntry(amina.getId(), "BOGUS", "10.00"));
        assertEquals(1, count("credit_entries"));
    }

    @Test
    void aStudentHasOneCreditAccountAndItsLimitCannotBeNegative() {
        AppUser amina = student("amina");

        insertAccount(amina.getId(), "100.00");                // valid control
        assertEquals(1, count("credit_accounts"));
        assertThrows(DataAccessException.class, () -> insertAccount(amina.getId(), "200.00"));

        jdbc.update("delete from credit_accounts where student_id = ?", amina.getId());
        assertThrows(DataAccessException.class, () -> insertAccount(amina.getId(), "-1.00"));
        assertEquals(0, count("credit_accounts"));
    }
}
