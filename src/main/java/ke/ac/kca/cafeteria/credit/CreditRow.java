package ke.ac.kca.cafeteria.credit;

/** One student on the manager's credit page: who, their figures as text, and what the forms can offer. */
public final class CreditRow {

    private final String studentPublicId;
    private final String displayName;
    private final String username;
    private final CreditBalance balance;

    public CreditRow(String studentPublicId, String displayName, String username, CreditBalance balance) {
        this.studentPublicId = studentPublicId;
        this.displayName = displayName;
        this.username = username;
        this.balance = balance;
    }

    public String getStudentPublicId() {
        return studentPublicId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public CreditBalance getBalance() {
        return balance;
    }

    /** The limit as a plain number for the form box, for example "500.00". */
    public String getLimitInput() {
        return balance.getLimit().toPlainString();
    }

    /** True while the student owes something, so the "record a payment" form is offered. */
    public boolean isCanSettle() {
        return balance.getOwed().signum() > 0;
    }
}
