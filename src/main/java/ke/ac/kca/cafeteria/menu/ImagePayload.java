package ke.ac.kca.cafeteria.menu;

/** A stored photo ready to send to the browser. */
public record ImagePayload(String contentType, byte[] data, long version) {
}
