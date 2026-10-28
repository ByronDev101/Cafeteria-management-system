package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A student's credit limit, set by a manager. A student with no account has no credit. The limit is the most the
 * student may have held or owed at once; what is held and owed comes from the ledger, never from this row.
 */
@Entity
@Table(name = "credit_accounts")
public class CreditAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private AppUser student;

    @Column(name = "credit_limit", nullable = false, columnDefinition = "decimal(12,2)")
    private BigDecimal creditLimit;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    protected CreditAccount() {
    }

    public CreditAccount(AppUser student, BigDecimal creditLimit, String updatedBy) {
        this.student = student;
        this.creditLimit = requireLimit(creditLimit);
        this.updatedBy = updatedBy;
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

    public void changeLimit(BigDecimal newLimit, String actorUsername) {
        this.creditLimit = requireLimit(newLimit);
        this.updatedBy = actorUsername;
    }

    private static BigDecimal requireLimit(BigDecimal limit) {
        if (limit == null || limit.signum() < 0) {
            throw new IllegalArgumentException("A credit limit cannot be negative");
        }
        return limit;
    }

    public Long getId() {
        return id;
    }

    public AppUser getStudent() {
        return student;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
