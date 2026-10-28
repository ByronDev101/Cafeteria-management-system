package ke.ac.kca.cafeteria.finance;

import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.OrderStatus;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The payment record for one order. Staff write it down by hand: no card, mobile-money PIN or bank detail is ever
 * collected, and no money moves through this system, so every record is labelled as a prototype record. The amount
 * is copied from the order when the record is opened. The status only changes through the methods below, which
 * enforce the allowed moves in {@link PaymentStatus}.
 */
@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "method_code", nullable = false, updatable = false, length = 20)
    private String methodCode;

    @Column(nullable = false, updatable = false, columnDefinition = "decimal(12,2)")
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    private String statusCode;

    @Column(length = 40)
    private String reference;

    @Column(nullable = false)
    private boolean prototype = true;

    @Column(name = "verified_by", length = 50)
    private String verifiedBy;

    @Column(name = "verified_at", columnDefinition = "datetime(6)")
    private Instant verifiedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    protected PaymentRecord() {
    }

    /** Opens the record for an order: awaiting payment, or voided if the order is somehow already cancelled. */
    public PaymentRecord(CustomerOrder order) {
        this.publicId = UUID.randomUUID().toString();
        this.order = order;
        this.methodCode = order.getPaymentMethod().name();
        this.amount = order.getTotal();
        this.statusCode = (order.getStatus() == OrderStatus.CANCELLED ? PaymentStatus.VOIDED : PaymentStatus.PENDING).name();
        this.prototype = true;
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

    /** Staff have received the payment. The reference (for example an M-Pesa code) is optional. */
    public void markReceived(String actorUsername, String reference) {
        moveTo(PaymentStatus.VERIFIED);
        this.reference = reference;
        this.verifiedBy = actorUsername;
        this.verifiedAt = Instant.now();
    }

    /** The order was cancelled before any payment was taken. */
    public void markVoided() {
        moveTo(PaymentStatus.VOIDED);
    }

    /** The order was cancelled after the payment was received, so the money is owed back. */
    public void markRefundDue() {
        moveTo(PaymentStatus.REFUND_DUE);
    }

    /** Staff have handed the money back. */
    public void markRefunded() {
        moveTo(PaymentStatus.REFUNDED);
    }

    private void moveTo(PaymentStatus next) {
        PaymentStatus current = getStatus();
        if (!current.canMoveTo(next)) {
            throw new IllegalStateException("A payment cannot move from " + current + " to " + next);
        }
        statusCode = next.name();
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public String getMethodCode() {
        return methodCode;
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.valueOf(methodCode);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return PaymentStatus.valueOf(statusCode);
    }

    public String getReference() {
        return reference;
    }

    public boolean isPrototype() {
        return prototype;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
