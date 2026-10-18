package ke.ac.kca.cafeteria.ordering;

/** The result of placing an order. {@code duplicate} is true when a repeated tap found the order already created. */
public record PlacedOrder(String publicId, String orderNumber, boolean duplicate) {
}
