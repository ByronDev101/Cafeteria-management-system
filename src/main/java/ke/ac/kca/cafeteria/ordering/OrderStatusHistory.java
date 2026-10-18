package ke.ac.kca.cafeteria.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** One step in an order's life: who moved it, from where, to where, and when. Append-only (FR-021). */
@Entity
@Immutable
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "from_status", updatable = false, length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private String toStatus;

    @Column(name = "actor_username", nullable = false, updatable = false, length = 50)
    private String actorUsername;

    @Column(updatable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected OrderStatusHistory() {
    }

    OrderStatusHistory(CustomerOrder order, OrderStatus from, OrderStatus to, String actorUsername, String reason) {
        this.order = order;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.actorUsername = actorUsername;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public OrderStatus getFromStatus() {
        return fromStatus == null ? null : OrderStatus.valueOf(fromStatus);
    }

    public OrderStatus getToStatus() {
        return OrderStatus.valueOf(toStatus);
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
