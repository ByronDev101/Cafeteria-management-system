package ke.ac.kca.cafeteria.reporting;

/** An audit event prepared for display, with the time already in campus time. */
public final class AuditRow {

    private final String when;
    private final String actor;
    private final String action;
    private final String target;
    private final String outcome;
    private final String details;

    AuditRow(String when, String actor, String action, String target, String outcome, String details) {
        this.when = when;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.outcome = outcome;
        this.details = details;
    }

    public String getWhen() {
        return when;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetails() {
        return details;
    }
}
