package ke.ac.kca.cafeteria.menu;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fictional demo menu for the "dev" profile only (NFR-04). It creates the menu when empty, fills in prep
 * times and a few specials once, and attaches photos found in src/main/resources/demo-photos, named after the
 * item (for example chicken-and-rice.jpg), to items that have none.
 */
@Component
@Profile("dev")
public class DemoMenuSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoMenuSeeder.class);

    private record Extras(int prepMinutes, boolean vegetarian) {
    }

    private static final Map<String, Extras> EXTRAS = Map.of(
            "Chicken and Rice", new Extras(12, false),
            "Beef Stew with Ugali", new Extras(10, false),
            "Vegetable Pilau", new Extras(8, true),
            "Tea", new Extras(3, true),
            "Fresh Juice", new Extras(4, true),
            "Soda", new Extras(1, true),
            "Samosa", new Extras(5, false),
            "Chips", new Extras(9, true),
            "Mandazi", new Extras(4, true));

    private static final List<String> SPECIALS = List.of("Chicken and Rice", "Vegetable Pilau", "Beef Stew with Ugali");

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final MenuImageStore imageStore;

    public DemoMenuSeeder(MenuCategoryRepository categories, MenuItemRepository items, MenuImageStore imageStore) {
        this.categories = categories;
        this.items = items;
        this.imageStore = imageStore;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categories.count() == 0) {
            seedMenu();
        }
        fillDesignFieldsOnce();
        attachDemoPhotos();
    }

    private void seedMenu() {
        MenuCategory meals = categories.save(new MenuCategory("Meals", "Hot lunch and dinner plates", 1));
        MenuCategory drinks = categories.save(new MenuCategory("Drinks", "Hot and cold drinks", 2));
        MenuCategory snacks = categories.save(new MenuCategory("Snacks", "Light bites", 3));

        add(meals, "Chicken and Rice", "Roast chicken quarter with steamed rice", "350");
        add(meals, "Beef Stew with Ugali", "Slow-cooked beef stew and ugali", "300");
        add(meals, "Vegetable Pilau", "Spiced rice with seasonal vegetables", "250");

        add(drinks, "Tea", "Hot milk tea", "50");
        MenuItem juice = add(drinks, "Fresh Juice", "Seasonal fruit juice", "120");
        juice.setAvailable(false);            // shows how a sold-out item looks
        add(drinks, "Soda", "Chilled bottled soda", "80");

        add(snacks, "Samosa", "Beef samosa", "50");
        add(snacks, "Chips", "Fried potato chips", "150");
        add(snacks, "Mandazi", "Sweet fried dough", "30");
        log.info("Seeded fictional demo menu (dev profile only)");
    }

    /** Runs only when no item has a prep time yet, so later edits by a manager are never overwritten. */
    private void fillDesignFieldsOnce() {
        List<MenuItem> all = items.findAll();
        if (all.stream().anyMatch(item -> item.getPrepMinutes() != null)) {
            return;
        }
        for (MenuItem item : all) {
            Extras extras = EXTRAS.get(item.getName());
            if (extras != null) {
                item.setPrepMinutes(extras.prepMinutes());
                item.setVegetarian(extras.vegetarian());
            }
            if (SPECIALS.contains(item.getName())) {
                item.setFeatured(true);
            }
        }
        log.info("Filled in demo prep times and today's specials (dev profile only)");
    }

    private void attachDemoPhotos() {
        Resource[] photos;
        try {
            photos = new PathMatchingResourcePatternResolver().getResources("classpath*:demo-photos/*");
        } catch (IOException e) {
            return;
        }
        for (Resource photo : photos) {
            String filename = photo.getFilename();
            if (filename == null || !filename.contains(".")) {
                continue;
            }
            String slug = filename.substring(0, filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            for (MenuItem item : items.findAll()) {
                if (item.getImageVersion() == null && slug(item.getName()).equals(slug)) {
                    attach(item, photo);
                }
            }
        }
    }

    private void attach(MenuItem item, Resource photo) {
        try (InputStream in = photo.getInputStream()) {
            imageStore.save(item, ImageProcessor.process(in.readAllBytes()));
            log.info("Attached demo photo to {}", item.getName());
        } catch (IOException | MenuException e) {
            log.warn("Could not use demo photo {}: {}", photo.getFilename(), e.getMessage());
        }
    }

    static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private MenuItem add(MenuCategory category, String name, String description, String price) {
        return items.save(new MenuItem(category, name, description, new BigDecimal(price).setScale(2)));
    }
}
