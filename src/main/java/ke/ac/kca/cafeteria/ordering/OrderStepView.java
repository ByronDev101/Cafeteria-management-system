package ke.ac.kca.cafeteria.ordering;

/** One step of the progress tracker. {@code state} is done, current, upcoming or cancelled. */
public final class OrderStepView {

    private final String label;
    private final String state;
    private final String timeText;

    public OrderStepView(String label, String state, String timeText) {
        this.label = label;
        this.state = state;
        this.timeText = timeText;
    }

    public String getLabel() {
        return label;
    }

    public String getState() {
        return state;
    }

    public String getTimeText() {
        return timeText;
    }
}
