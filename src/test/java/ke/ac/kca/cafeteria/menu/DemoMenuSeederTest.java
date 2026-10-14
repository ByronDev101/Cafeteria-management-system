package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DemoMenuSeederTest {

    @Test
    void photoFileNamesMatchItemNames() {
        assertEquals("chicken-and-rice", DemoMenuSeeder.slug("Chicken and Rice"));
        assertEquals("beef-stew-with-ugali", DemoMenuSeeder.slug("Beef Stew with Ugali"));
        assertEquals("tea", DemoMenuSeeder.slug("  Tea!  "));
    }
}
