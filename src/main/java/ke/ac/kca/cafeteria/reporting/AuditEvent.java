package ke.ac.kca.cafeteria.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** One recorded action. Append-only: no setters, and Hibernate will never issue an UPDATE for it. */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant occurredAt;

    @Column(name = "actor_username", nullable = false, updatable = false, length = 50)
    private String actorUsername;

    @Column(nullable = false, updatable = false, length = 50)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false, length = 64)
    private String targetId;

    @Column(nullable = false, updatable = false, length = 20)
    private String outcome;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(updatable = false, length = 500)
    private String details;

    protected AuditEvent() {
    }

    public AuditEvent(String actorUsername, String action, String targetType, String targetId,
                      String outcome, String details) {
        this.occurredAt = Instant.now();
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDetails() {
        return details;
    }
}
