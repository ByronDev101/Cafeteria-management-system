package ke.ac.kca.cafeteria.table;

/** A freshly issued token. The plain token exists only in this object and on the printed label; it is never stored. */
public record IssuedQr(String publicId, String label, String token) {

    @Override
    public String toString() {
        return "IssuedQr[" + label + "]";
    }
}
