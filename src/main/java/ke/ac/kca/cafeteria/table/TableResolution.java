package ke.ac.kca.cafeteria.table;

/** Outcome of looking up a QR token. The screens treat every failure the same way so nothing is revealed. */
public record TableResolution(Status status, SelectedTable table) {

    public enum Status { VALID, MALFORMED, UNKNOWN, INACTIVE }

    public static TableResolution valid(SelectedTable table) {
        return new TableResolution(Status.VALID, table);
    }

    public static TableResolution failed(Status status) {
        return new TableResolution(status, null);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }
}
