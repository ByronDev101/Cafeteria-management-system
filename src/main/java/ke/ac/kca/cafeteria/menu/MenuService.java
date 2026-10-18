package ke.ac.kca.cafeteria.menu;

import ke.ac.kca.cafeteria.reporting.AuditService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menu management and display (FR-006 to FR-008). Every change and its audit record commit together.
 * The actor always comes from the signed-in session, never from the request.
 */
@Service
public class MenuService {

    private static final String CATEGORY = "MENU_CATEGORY";
    private static final String ITEM = "MENU_ITEM";
    private static final String SUCCESS = "SUCCESS";
    private static final int MAX_HERO_SLIDES = 3;

    static final String CATEGORY_CREATED = "MENU_CATEGORY_CREATED";
    static final String CATEGORY_UPDATED = "MENU_CATEGORY_UPDATED";
    static final String CATEGORY_ACTIVATED = "MENU_CATEGORY_ACTIVATED";
    static final String CATEGORY_DEACTIVATED = "MENU_CATEGORY_DEACTIVATED";
    static final String ITEM_CREATED = "MENU_ITEM_CREATED";
    static final String ITEM_UPDATED = "MENU_ITEM_UPDATED";
    static final String PRICE_CHANGED = "MENU_PRICE_CHANGED";
    static final String ITEM_AVAILABILITY = "MENU_ITEM_AVAILABILITY_CHANGED";
    static final String ITEM_ACTIVATED = "MENU_ITEM_ACTIVATED";
    static final String ITEM_DEACTIVATED = "MENU_ITEM_DEACTIVATED";
    static final String IMAGE_CHANGED = "MENU_ITEM_IMAGE_CHANGED";
    static final String IMAGE_REMOVED = "MENU_ITEM_IMAGE_REMOVED";

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final MenuImageStore imageStore;
    private final AuditService audit;

    public MenuService(MenuCategoryRepository categories, MenuItemRepository items,
                       MenuImageStore imageStore, AuditService audit) {
        this.categories = categories;
        this.items = items;
        this.imageStore = imageStore;
        this.audit = audit;
    }

    // ---- reading ----

    /** Everything, including inactive and unavailable entries, for managers. */
    @Transactional(readOnly = true)
    public List<CategoryView> managerMenu() {
        List<CategoryView> result = new ArrayList<>();
        for (MenuCategory category : categories.findAllByOrderByDisplayOrderAscNameAsc()) {
            result.add(CategoryView.from(category, itemViews(category, false)));
        }
        return result;
    }

    /** What students see: active categories and active items. Sold-out items stay visible but marked. */
    @Transactional(readOnly = true)
    public List<CategoryView> studentMenu() {
        List<CategoryView> result = new ArrayList<>();
        for (MenuCategory category : categories.findByActiveTrueOrderByDisplayOrderAscNameAsc()) {
            List<ItemView> visible = itemViews(category, true);
            if (!visible.isEmpty()) {
                result.add(CategoryView.from(category, visible));
            }
        }
        return result;
    }

    /** Today's specials for the hero banner: featured items that can be ordered now, at most three. */
    public static List<ItemView> featured(List<CategoryView> sections) {
        return sections.stream()
                .flatMap(section -> section.getItems().stream())
                .filter(item -> item.isFeatured() && item.isAvailable())
                .limit(MAX_HERO_SLIDES)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryView category(String publicId) {
        return CategoryView.from(findCategory(publicId), List.of());
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categoryOptions() {
        return categories.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(category -> CategoryView.from(category, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemView item(String publicId) {
        return ItemView.from(findItem(publicId));
    }

    @Transactional(readOnly = true)
    public Optional<ImagePayload> image(String itemPublicId) {
        return items.findByPublicId(itemPublicId).flatMap(imageStore::load);
    }

    /**
     * Looks up items for the cart and checkout with today's price and whether they can be ordered right now.
     * IDs that match no item are simply missing from the result.
     */
    @Transactional(readOnly = true)
    public Map<String, OrderableItem> lookup(Collection<String> publicIds) {
        Map<String, OrderableItem> result = new LinkedHashMap<>();
        for (String publicId : publicIds) {
            items.findByPublicId(publicId).ifPresent(item -> result.put(publicId, orderable(item)));
        }
        return result;
    }

    private static OrderableItem orderable(MenuItem item) {
        boolean listed = item.isActive() && item.getCategory().isActive();
        String problem = !listed ? "no longer on the menu" : (!item.isAvailable() ? "sold out" : null);
        return new OrderableItem(item.getId(), item.getPublicId(), item.getName(), item.getPrice(),
                problem == null, problem, item.getImageVersion());
    }

    // ---- categories ----

    @Transactional
    public void createCategory(String actor, String name, String description, String displayOrder) {
        String cleanName = clean(name);
        String cleanDescription = clean(description);
        List<String> problems = new ArrayList<>(validateCategory(cleanName, cleanDescription));
        int order = parseOrder(displayOrder, problems);
        if (problems.isEmpty() && categories.existsByNameIgnoreCase(cleanName)) {
            problems.add("A category with that name already exists.");
        }
        failIfAny(problems);

        MenuCategory category = new MenuCategory(cleanName, blankToNull(cleanDescription), order);
        categories.save(category);
        audit.record(actor, CATEGORY_CREATED, CATEGORY, category.getPublicId(), SUCCESS, "name=" + cleanName);
    }

    @Transactional
    public void updateCategory(String actor, String publicId, String name, String description, String displayOrder) {
        MenuCategory category = findCategory(publicId);
        String cleanName = clean(name);
        String cleanDescription = clean(description);
        List<String> problems = new ArrayList<>(validateCategory(cleanName, cleanDescription));
        int order = parseOrder(displayOrder, problems);
        if (problems.isEmpty() && categories.existsByNameIgnoreCaseAndIdNot(cleanName, category.getId())) {
            problems.add("A category with that name already exists.");
        }
        failIfAny(problems);

        String before = category.getName() + "|" + nullToEmpty(category.getDescription()) + "|" + category.getDisplayOrder();
        category.update(cleanName, blankToNull(cleanDescription), order);
        String after = category.getName() + "|" + nullToEmpty(category.getDescription()) + "|" + category.getDisplayOrder();
        if (!before.equals(after)) {
            audit.record(actor, CATEGORY_UPDATED, CATEGORY, category.getPublicId(), SUCCESS,
                    "name=" + cleanName + "; order=" + order);
        }
    }

    @Transactional
    public void setCategoryActive(String actor, String publicId, boolean active) {
        MenuCategory category = findCategory(publicId);
        if (category.isActive() == active) {
            return;
        }
        category.setActive(active);
        audit.record(actor, active ? CATEGORY_ACTIVATED : CATEGORY_DEACTIVATED, CATEGORY,
                category.getPublicId(), SUCCESS, "name=" + category.getName());
    }

    // ---- items ----

    /** Creates an item. photo is optional raw upload bytes; an unusable photo stops the whole save. */
    @Transactional
    public void createItem(String actor, ItemInput input, byte[] photo) {
        MenuCategory category = findCategory(input.categoryId());
        String cleanName = clean(input.name());
        String cleanDescription = clean(input.description());
        List<String> problems = new ArrayList<>(validateItem(cleanName, cleanDescription));
        BigDecimal price = parsePrice(input.price(), problems);
        Integer prep = parsePrep(input.prepMinutes(), problems);
        ImageProcessor.Processed processed = processPhoto(photo, problems);
        if (!category.isActive()) {
            problems.add("Choose an active category.");
        }
        if (problems.isEmpty() && items.existsByCategoryAndNameIgnoreCase(category, cleanName)) {
            problems.add("That category already has an item with this name.");
        }
        failIfAny(problems);

        MenuItem item = new MenuItem(category, cleanName, blankToNull(cleanDescription), price);
        item.setPrepMinutes(prep);
        item.setVegetarian(input.vegetarian());
        item.setFeatured(input.featured());
        items.save(item);
        audit.record(actor, ITEM_CREATED, ITEM, item.getPublicId(), SUCCESS,
                "name=" + cleanName + "; category=" + category.getName() + "; price=" + price.toPlainString());
        if (processed != null) {
            imageStore.save(item, processed);
            audit.record(actor, IMAGE_CHANGED, ITEM, item.getPublicId(), SUCCESS,
                    "item=" + cleanName + "; size=" + processed.jpeg().length + " bytes");
        }
    }

    @Transactional
    public void updateItem(String actor, String itemPublicId, ItemInput input, byte[] photo) {
        MenuItem item = findItem(itemPublicId);
        MenuCategory category = findCategory(input.categoryId());
        String cleanName = clean(input.name());
        String cleanDescription = clean(input.description());
        List<String> problems = new ArrayList<>(validateItem(cleanName, cleanDescription));
        BigDecimal newPrice = parsePrice(input.price(), problems);
        Integer prep = parsePrep(input.prepMinutes(), problems);
        ImageProcessor.Processed processed = processPhoto(photo, problems);
        boolean movingCategory = !Objects.equals(item.getCategory().getPublicId(), category.getPublicId());
        if (movingCategory && !category.isActive()) {
            problems.add("Choose an active category.");
        }
        if (problems.isEmpty()
                && items.existsByCategoryAndNameIgnoreCaseAndIdNot(category, cleanName, item.getId())) {
            problems.add("That category already has an item with this name.");
        }
        failIfAny(problems);

        BigDecimal oldPrice = item.getPrice();
        String before = signature(item);
        item.update(category, cleanName, blankToNull(cleanDescription), newPrice,
                prep, input.vegetarian(), input.featured());
        String after = signature(item);

        if (oldPrice.compareTo(newPrice) != 0) {
            audit.record(actor, PRICE_CHANGED, ITEM, item.getPublicId(), SUCCESS,
                    "item=" + cleanName + "; price: " + oldPrice.toPlainString() + " -> " + newPrice.toPlainString());
        }
        if (!before.equals(after)) {
            audit.record(actor, ITEM_UPDATED, ITEM, item.getPublicId(), SUCCESS,
                    "name=" + cleanName + "; category=" + category.getName());
        }
        if (processed != null) {
            imageStore.save(item, processed);
            audit.record(actor, IMAGE_CHANGED, ITEM, item.getPublicId(), SUCCESS,
                    "item=" + cleanName + "; size=" + processed.jpeg().length + " bytes");
        }
    }

    @Transactional
    public void removeItemImage(String actor, String publicId) {
        MenuItem item = findItem(publicId);
        if (item.getImageVersion() == null) {
            return;
        }
        imageStore.remove(item);
        audit.record(actor, IMAGE_REMOVED, ITEM, item.getPublicId(), SUCCESS, "item=" + item.getName());
    }

    @Transactional
    public void setItemAvailable(String actor, String publicId, boolean available) {
        MenuItem item = findItem(publicId);
        if (item.isAvailable() == available) {
            return;
        }
        item.setAvailable(available);
        audit.record(actor, ITEM_AVAILABILITY, ITEM, item.getPublicId(), SUCCESS,
                "item=" + item.getName() + "; available=" + available);
    }

    @Transactional
    public void setItemActive(String actor, String publicId, boolean active) {
        MenuItem item = findItem(publicId);
        if (item.isActive() == active) {
            return;
        }
        item.setActive(active);
        audit.record(actor, active ? ITEM_ACTIVATED : ITEM_DEACTIVATED, ITEM, item.getPublicId(), SUCCESS,
                "item=" + item.getName());
    }

    // ---- helpers ----

    private List<ItemView> itemViews(MenuCategory category, boolean activeOnly) {
        return items.findByCategoryOrderByNameAsc(category).stream()
                .filter(item -> !activeOnly || item.isActive())
                .map(ItemView::from)
                .toList();
    }

    private MenuCategory findCategory(String publicId) {
        return categories.findByPublicId(publicId)
                .orElseThrow(() -> new MenuException("That category was not found."));
    }

    private MenuItem findItem(String publicId) {
        return items.findByPublicId(publicId)
                .orElseThrow(() -> new MenuException("That item was not found."));
    }

    /** Everything about an item except price and photo, to tell whether a save changed anything. */
    private static String signature(MenuItem item) {
        return item.getName() + "|" + nullToEmpty(item.getDescription()) + "|" + item.getCategory().getPublicId()
                + "|" + item.getPrepMinutes() + "|" + item.isVegetarian() + "|" + item.isFeatured();
    }

    private static List<String> validateCategory(String name, String description) {
        List<String> problems = new ArrayList<>();
        if (name.length() < 2 || name.length() > 80) {
            problems.add("The category name must be 2 to 80 characters.");
        }
        if (description.length() > 255) {
            problems.add("The description must be at most 255 characters.");
        }
        return problems;
    }

    private static List<String> validateItem(String name, String description) {
        List<String> problems = new ArrayList<>();
        if (name.length() < 2 || name.length() > 100) {
            problems.add("The item name must be 2 to 100 characters.");
        }
        if (description.length() > 500) {
            problems.add("The description must be at most 500 characters.");
        }
        return problems;
    }

    private static BigDecimal parsePrice(String text, List<String> problems) {
        try {
            return PriceParser.parse(text);
        } catch (MenuException e) {
            problems.addAll(e.getMessages());
            return BigDecimal.ZERO;
        }
    }

    private static Integer parsePrep(String text, List<String> problems) {
        String trimmed = clean(text);
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value >= 1 && value <= 180) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the message below
        }
        problems.add("The prep time must be a whole number of minutes from 1 to 180, or left empty.");
        return null;
    }

    private static ImageProcessor.Processed processPhoto(byte[] photo, List<String> problems) {
        if (photo == null || photo.length == 0) {
            return null;
        }
        try {
            return ImageProcessor.process(photo);
        } catch (MenuException e) {
            problems.addAll(e.getMessages());
            return null;
        }
    }

    private static int parseOrder(String text, List<String> problems) {
        String trimmed = clean(text);
        if (trimmed.isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value >= 0 && value <= 9999) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the message below
        }
        problems.add("The display order must be a whole number from 0 to 9999.");
        return 0;
    }

    private static void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new MenuException(problems);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static String blankToNull(String text) {
        return text.isEmpty() ? null : text;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
