package ke.ac.kca.cafeteria.ordering;

/** One line of the cart as shown to the student, priced from today's menu. */
public final class CartLine {

    private final String itemId;
    private final String name;
    private final String unitPriceText;
    private final int quantity;
    private final String lineTotalText;
    private final boolean orderable;
    private final String problem;
    private final Long imageVersion;

    public CartLine(String itemId, String name, String unitPriceText, int quantity, String lineTotalText,
                    boolean orderable, String problem, Long imageVersion) {
        this.itemId = itemId;
        this.name = name;
        this.unitPriceText = unitPriceText;
        this.quantity = quantity;
        this.lineTotalText = lineTotalText;
        this.orderable = orderable;
        this.problem = problem;
        this.imageVersion = imageVersion;
    }

    public String getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public String getUnitPriceText() {
        return unitPriceText;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getLineTotalText() {
        return lineTotalText;
    }

    public boolean isOrderable() {
        return orderable;
    }

    public String getProblem() {
        return problem;
    }

    public Long getImageVersion() {
        return imageVersion;
    }
}
