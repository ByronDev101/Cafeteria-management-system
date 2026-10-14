package ke.ac.kca.cafeteria.menu;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One menu item as the screens need it. */
public final class ItemView {

    /** Items at or below this price (KSh) count as budget friendly. */
    public static final BigDecimal BUDGET_MAX = new BigDecimal("200.00");
    /** Items ready in this many minutes or fewer count as fast prep. */
    public static final int FAST_MAX_MINUTES = 10;

    private final String publicId;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final boolean available;
    private final boolean active;
    private final String categoryPublicId;
    private final String categoryName;
    private final Integer prepMinutes;
    private final boolean vegetarian;
    private final boolean featured;
    private final Long imageVersion;

    public ItemView(String publicId, String name, String description, BigDecimal price,
                    boolean available, boolean active, String categoryPublicId, String categoryName,
                    Integer prepMinutes, boolean vegetarian, boolean featured, Long imageVersion) {
        this.publicId = publicId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.available = available;
        this.active = active;
        this.categoryPublicId = categoryPublicId;
        this.categoryName = categoryName;
        this.prepMinutes = prepMinutes;
        this.vegetarian = vegetarian;
        this.featured = featured;
        this.imageVersion = imageVersion;
    }

    /** Shorter form for places that do not care about the design fields. */
    public ItemView(String publicId, String name, String description, BigDecimal price,
                    boolean available, boolean active, String categoryPublicId, String categoryName) {
        this(publicId, name, description, price, available, active, categoryPublicId, categoryName,
                null, false, false, null);
    }

    static ItemView from(MenuItem item) {
        return new ItemView(item.getPublicId(), item.getName(), item.getDescription(), item.getPrice(),
                item.isAvailable(), item.isActive(),
                item.getCategory().getPublicId(), item.getCategory().getName(),
                item.getPrepMinutes(), item.isVegetarian(), item.isFeatured(), item.getImageVersion());
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

    public Integer getPrepMinutes() {
        return prepMinutes;
    }

    /** For example "12 min", or null when no prep time is set. */
    public String getPrepText() {
        return prepMinutes == null ? null : prepMinutes + " min";
    }

    public boolean isVegetarian() {
        return vegetarian;
    }

    public boolean isFeatured() {
        return featured;
    }

    public Long getImageVersion() {
        return imageVersion;
    }

    public boolean isFast() {
        return prepMinutes != null && prepMinutes <= FAST_MAX_MINUTES;
    }

    public boolean isBudget() {
        return price.compareTo(BUDGET_MAX) <= 0;
    }

    /** Filter tags used by the student menu: vegetarian, budget, fast. */
    public List<String> getTags() {
        List<String> tags = new ArrayList<>();
        if (vegetarian) {
            tags.add("vegetarian");
        }
        if (isBudget()) {
            tags.add("budget");
        }
        if (isFast()) {
            tags.add("fast");
        }
        return tags;
    }

    public String getTagText() {
        return String.join(" ", getTags());
    }

    /** Lower-case text the search box matches against. */
        /** Lower-case text the search box matches against, with single spaces between the parts. */
    public String getSearchText() {
        StringBuilder text = new StringBuilder(name);
        if (description != null && !description.isBlank()) {
            text.append(' ').append(description);
        }
        text.append(' ').append(categoryName);
        return text.toString().toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
