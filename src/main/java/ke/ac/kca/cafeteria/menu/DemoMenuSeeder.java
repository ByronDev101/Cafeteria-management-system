package ke.ac.kca.cafeteria.menu;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fictional menu for demos, created in the "dev" profile only and only when the menu is empty (NFR-04). */
@Component
@Profile("dev")
public class DemoMenuSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoMenuSeeder.class);

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;

    public DemoMenuSeeder(MenuCategoryRepository categories, MenuItemRepository items) {
        this.categories = categories;
        this.items = items;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categories.count() > 0) {
            return;
        }
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

    private MenuItem add(MenuCategory category, String name, String description, String price) {
        return items.save(new MenuItem(category, name, description, new BigDecimal(price).setScale(2)));
    }
}
