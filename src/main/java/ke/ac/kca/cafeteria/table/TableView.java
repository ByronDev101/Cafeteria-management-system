package ke.ac.kca.cafeteria.table;

/** One table as the admin list needs it. */
public final class TableView {

    private final String publicId;
    private final String label;
    private final boolean active;
    private final String issuedText;

    public TableView(String publicId, String label, boolean active, String issuedText) {
        this.publicId = publicId;
        this.label = label;
        this.active = active;
        this.issuedText = issuedText;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    /** When the current QR was issued, in campus time. */
    public String getIssuedText() {
        return issuedText;
    }
}
