package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderNumberAndPaymentTest {

    @Test
    void orderNumbersAreReadableAndNotSequential() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String number = OrderNumber.generate();
            assertTrue(number.matches("SC-[0-9A-HJKMNP-TV-Z]{6}"), number);
            seen.add(number);
        }
        assertTrue(seen.size() > 4990);
    }

    @Test
    void onlyCashAndMpesaAreOfferedForNow() {
        assertEquals(List.of(PaymentMethod.CASH, PaymentMethod.MPESA_TILL), PaymentMethod.offered());
    }

    @Test
    void paymentCodesAreParsedStrictly() {
        assertEquals(PaymentMethod.CASH, PaymentMethod.parse("CASH").orElseThrow());
        assertEquals(PaymentMethod.MPESA_TILL, PaymentMethod.parse(" MPESA_TILL ").orElseThrow());
        assertTrue(PaymentMethod.parse("cash").isEmpty());
        assertTrue(PaymentMethod.parse("bitcoin").isEmpty());
        assertTrue(PaymentMethod.parse(null).isEmpty());
    }
}
