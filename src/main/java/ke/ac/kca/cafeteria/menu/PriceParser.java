package ke.ac.kca.cafeteria.menu;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads and shows KSh prices (FR-008). Prices are exact decimals with two places, never negative. */
public final class PriceParser {

    private static final Pattern PRICE = Pattern.compile("^\\d{1,10}(\\.\\d{1,2})?$");

    private PriceParser() {
    }

    public static BigDecimal parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!PRICE.matcher(trimmed).matches()) {
            throw new MenuException("Enter the price in KSh as a number, for example 150 or 150.50.");
        }
        return new BigDecimal(trimmed).setScale(2);
    }

    public static String format(BigDecimal price) {
        return "KSh " + String.format(Locale.ROOT, "%,.2f", price);
    }
}
