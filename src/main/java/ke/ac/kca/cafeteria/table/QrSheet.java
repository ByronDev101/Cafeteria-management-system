package ke.ac.kca.cafeteria.table;

/** Everything the printable QR card shows. Lives in the session for one page view, then is gone. */
public final class QrSheet implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final String label;
    private final String code;
    private final String url;
    private final String dataUri;

    public QrSheet(String label, String code, String url, String dataUri) {
        this.label = label;
        this.code = code;
        this.url = url;
        this.dataUri = dataUri;
    }

    public String getLabel() {
        return label;
    }

    /** The code in groups of four, for typing in by hand. */
    public String getCode() {
        return code;
    }

    public String getUrl() {
        return url;
    }

    /** The QR picture as a data: URI, so no file is ever written. */
    public String getDataUri() {
        return dataUri;
    }
}
