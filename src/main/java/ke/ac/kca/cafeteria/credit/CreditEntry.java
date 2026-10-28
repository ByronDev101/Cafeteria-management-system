package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One line in the credit ledger. Lines are only ever added: there are no setters, the entity is immutable, and the
 * repository offers no update or delete. A mistake is corrected by adding a line that cancels it.
 */
@Entity
@Immutable
@Table(name = "credit_entries")
public class CreditEntry {

    public static final int NOTE_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private CustomerOrder order;

    @Column(name = "entry_type", nullable = false, updatable = false, length = 20)
    private String entryTypeCode;

    @Column(nullable = false, updatable = false, columnDefinition = "decimal(12,2)")
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 50)
    private String actor;

    @Column(updatable = false, length = 200)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected CreditEntry() {
    }

    public CreditEntry(AppUser student, CustomerOrder order, CreditEntryType type, BigDecimal amount, String actor,
                       String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A ledger amount must be above zero");
        }
        this.publicId = UUID.randomUUID().toString();
        this.student = student;
        this.order = order;
        this.entryTypeCode = type.name();
        this.amount = amount;
        this.actor = actor;
        this.note = note;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public AppUser getStudent() {
        return student;
    }

    /** The order this line is about, or null for a payment recorded against the balance. */
    public CustomerOrder getOrder() {
        return order;
    }

    public CreditEntryType getType() {
        return CreditEntryType.valueOf(entryTypeCode);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getActor() {
        return actor;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
