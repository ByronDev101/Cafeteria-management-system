package ke.ac.kca.cafeteria.table;

import java.io.Serializable;

/** The table a student is ordering at. Kept in the session; it holds the public ID and label, never the token. */
public final class SelectedTable implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String publicId;
    private final String label;

    public SelectedTable(String publicId, String label) {
        this.publicId = publicId;
        this.label = label;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getLabel() {
        return label;
    }
}
