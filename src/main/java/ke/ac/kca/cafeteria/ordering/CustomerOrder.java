package ke.ac.kca.cafeteria.ordering;

import ke.ac.kca.cafeteria.identity.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An order placed by a student for a table (FR-015). Named CustomerOrder because ORDER is a reserved word in SQL.
 * The status only changes through {@link #moveTo}, which enforces the allowed transitions and records the history.
 */
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId;

    @Column(name = "order_number", nullable = false, unique = true, updatable = false, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private AppUser student;

    @Column(name = "table_id", nullable = false, updatable = false)
    private Long tableId;

    @Column(name = "table_label", nullable = false, updatable = false, length = 40)
    private String tableLabel;

    @Column(name = "status", nullable = false, length = 20)
    private String statusCode;

    @Column(name = "payment_method", nullable = false, updatable = false, length = 20)
    private String paymentCode;

    @Column(nullable = false, columnDefinition = "decimal(12,2)")
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Column(name = "idempotency_key", updatable = false, length = 64)
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNumber, AppUser student, Long tableId, String tableLabel,
                         PaymentMethod paymentMethod, String idempotencyKey) {
        this.publicId = UUID.randomUUID().toString();
        this.orderNumber = orderNumber;
        this.student = student;
        this.tableId = tableId;
        this.tableLabel = tableLabel;
        this.paymentCode = paymentMethod.name();
        this.idempotencyKey = idempotencyKey;
        this.statusCode = OrderStatus.SUBMITTED.name();
        this.history.add(new OrderStatusHistory(this, null, OrderStatus.SUBMITTED, student.getUsername(), null));
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Adds a line with its price copied from the menu, and keeps the order total in step. */
    public void addItem(Long menuItemId, String name, BigDecimal unitPrice, int quantity) {
        OrderItem item = new OrderItem(this, menuItemId, name, unitPrice, quantity);
        items.add(item);
        total = total.add(item.getLineTotal());
    }

    /** Moves the order along an allowed arrow and records who did it. Anything else leaves the order untouched. */
    public void moveTo(OrderStatus next, String actorUsername, String reason) {
        OrderStatus current = getStatus();
        if (!current.canMoveTo(next)) {
            throw new IllegalStateException("An order cannot move from " + current + " to " + next);
        }
        history.add(new OrderStatusHistory(this, current, next, actorUsername, reason));
        statusCode = next.name();
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public AppUser getStudent() {
        return student;
    }

    public Long getTableId() {
        return tableId;
    }

    public String getTableLabel() {
        return tableLabel;
    }

    public OrderStatus getStatus() {
        return OrderStatus.valueOf(statusCode);
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.valueOf(paymentCode);
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<OrderStatusHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
