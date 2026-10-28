package ke.ac.kca.cafeteria.reporting;

/** One line of a report table: a label, a style name for its colour, how many, and the amount as text. */
public final class ReportLine {

    private final String label;
    private final String styleClass;
    private final long count;
    private final String amountText;

    public ReportLine(String label, String styleClass, long count, String amountText) {
        this.label = label;
        this.styleClass = styleClass;
        this.count = count;
        this.amountText = amountText;
    }

    public String getLabel() {
        return label;
    }

    public String getStyleClass() {
        return styleClass;
    }

    public long getCount() {
        return count;
    }

    public String getAmountText() {
        return amountText;
    }
}
