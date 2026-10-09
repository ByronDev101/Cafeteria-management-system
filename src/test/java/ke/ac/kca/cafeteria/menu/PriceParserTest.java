package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceParserTest {

    @Test
    void parsesWholeAndDecimalPricesToTwoPlaces() {
        assertEquals(new BigDecimal("150.00"), PriceParser.parse("150"));
        assertEquals(new BigDecimal("150.50"), PriceParser.parse(" 150.5 "));
        assertEquals(new BigDecimal("0.00"), PriceParser.parse("0"));
        assertEquals(new BigDecimal("1250.00"), PriceParser.parse("1250.00"));
    }

    @Test
    void rejectsNegativeTextAndTooManyDecimals() {
        for (String bad : new String[] {"-5", "abc", "1.234", "", "  ", "1,250", "12345678901", "5."}) {
            assertThrows(MenuException.class, () -> PriceParser.parse(bad), bad);
        }
        assertThrows(MenuException.class, () -> PriceParser.parse(null));
    }

    @Test
    void formatsWithCurrencyAndThousandsSeparator() {
        assertEquals("KSh 350.00", PriceParser.format(new BigDecimal("350.00")));
        assertEquals("KSh 1,250.50", PriceParser.format(new BigDecimal("1250.50")));
    }
}
