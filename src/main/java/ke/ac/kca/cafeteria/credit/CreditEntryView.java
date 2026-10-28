package ke.ac.kca.cafeteria.credit;

/** One ledger line as the student sees it. */
public final class CreditEntryView {

    private final String typeLabel;
    private final String typeClass;
    private final String amountText;
    private final String orderNumber;
    private final String createdText;
    private final String note;

    public CreditEntryView(String typeLabel, String typeClass, String amountText, String orderNumber,
                           String createdText, String note) {
        this.typeLabel = typeLabel;
        this.typeClass = typeClass;
        this.amountText = amountText;
        this.orderNumber = orderNumber;
        this.createdText = createdText;
        this.note = note;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    /** Lower-case type name for styling, for example "reserve". */
    public String getTypeClass() {
        return typeClass;
    }

    public String getAmountText() {
        return amountText;
    }

    /** The order number, or null for a payment recorded against the balance. */
    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCreatedText() {
        return createdText;
    }

    public String getNote() {
        return note;
    }
}
