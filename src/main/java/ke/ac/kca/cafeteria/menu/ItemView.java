package ke.ac.kca.cafeteria.menu;

import java.math.BigDecimal;

/** One menu item as the screens need it. */
public final class ItemView {

    private final String publicId;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final boolean available;
    private final boolean active;
    private final String categoryPublicId;
    private final String categoryName;

    public ItemView(String publicId, String name, String description, BigDecimal price,
                    boolean available, boolean active, String categoryPublicId, String categoryName) {
        this.publicId = publicId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.available = available;
        this.active = active;
        this.categoryPublicId = categoryPublicId;
        this.categoryName = categoryName;
    }

    static ItemView from(MenuItem item) {
        return new ItemView(item.getPublicId(), item.getName(), item.getDescription(), item.getPrice(),
                item.isAvailable(), item.isActive(),
                item.getCategory().getPublicId(), item.getCategory().getName());
    }

    public String getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getPriceText() {
        return PriceParser.format(price);
    }

    /** Plain number for editing, for example 150.00. */
    public String getPriceInput() {
        return price.toPlainString();
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isActive() {
        return active;
    }

    public String getCategoryPublicId() {
        return categoryPublicId;
    }

    public String getCategoryName() {
        return categoryName;
    }
}
