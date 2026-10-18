package ke.ac.kca.cafeteria.ordering;

/** One line of a placed order, from its saved snapshot. */
public final class OrderLineView {

    private final String name;
    private final int quantity;
    private final String unitPriceText;
    private final String lineTotalText;

    public OrderLineView(String name, int quantity, String unitPriceText, String lineTotalText) {
        this.name = name;
        this.quantity = quantity;
        this.unitPriceText = unitPriceText;
        this.lineTotalText = lineTotalText;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getUnitPriceText() {
        return unitPriceText;
    }

    public String getLineTotalText() {
        return lineTotalText;
    }
}
