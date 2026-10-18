package ke.ac.kca.cafeteria.ordering;

import java.math.BigDecimal;
import java.util.List;

/** The whole cart as shown to the student. The total counts only items that can still be ordered. */
public final class CartView {

    private final List<CartLine> lines;
    private final BigDecimal total;
    private final String totalText;
    private final int unitCount;
    private final boolean canPlace;

    public CartView(List<CartLine> lines, BigDecimal total, String totalText, int unitCount, boolean canPlace) {
        this.lines = List.copyOf(lines);
        this.total = total;
        this.totalText = totalText;
        this.unitCount = unitCount;
        this.canPlace = canPlace;
    }

    public List<CartLine> getLines() {
        return lines;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getTotalText() {
        return totalText;
    }

    public int getUnitCount() {
        return unitCount;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** True when the cart has items and every one of them can still be ordered. */
    public boolean isCanPlace() {
        return canPlace;
    }
}
