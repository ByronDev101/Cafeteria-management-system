package ke.ac.kca.cafeteria.menu;

/** What the manager typed into the item form. Text fields stay text until the service validates them. */
public record ItemInput(String categoryId, String name, String description, String price,
                        String prepMinutes, boolean vegetarian, boolean featured) {
}
