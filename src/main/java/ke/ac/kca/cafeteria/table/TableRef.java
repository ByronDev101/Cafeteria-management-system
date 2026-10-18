package ke.ac.kca.cafeteria.table;

/** A table that is switched on, as the ordering module needs it. */
public record TableRef(Long id, String publicId, String label) {
}
