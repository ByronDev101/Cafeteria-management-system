package ke.ac.kca.cafeteria.menu;

import java.util.List;

/** One menu category with its items, as the screens need it. */
public final class CategoryView {

    private final String publicId;
    private final String name;
    private final String description;
    private final boolean active;
    private final int displayOrder;
    private final List<ItemView> items;

    public CategoryView(String publicId, String name, String description, boolean active,
                        int displayOrder, List<ItemView> items) {
        this.publicId = publicId;
        this.name = name;
        this.description = description;
        this.active = active;
        this.displayOrder = displayOrder;
        this.items = List.copyOf(items);
    }

    static CategoryView from(MenuCategory category, List<ItemView> items) {
        return new CategoryView(category.getPublicId(), category.getName(), category.getDescription(),
                category.isActive(), category.getDisplayOrder(), items);
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

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public List<ItemView> getItems() {
        return items;
    }
}
