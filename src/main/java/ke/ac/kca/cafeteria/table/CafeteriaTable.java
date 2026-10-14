package ke.ac.kca.cafeteria.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A physical cafeteria table. Holds only the hash of its QR token, never the token itself. */
@Entity
@Table(name = "cafeteria_tables")
public class CafeteriaTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "token_issued_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant tokenIssuedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    protected CafeteriaTable() {
    }

    public CafeteriaTable(String label, String tokenHash) {
        this.publicId = UUID.randomUUID().toString();
        this.label = label;
        this.tokenHash = tokenHash;
        this.tokenIssuedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    /** Switches the table off and replaces its token with a throw-away one, so the printed QR never works again. */
    public void deactivate(String discardedTokenHash) {
        this.active = false;
        this.tokenHash = discardedTokenHash;
        this.tokenIssuedAt = Instant.now();
    }

    /** Switches the table back on with a brand-new token. */
    public void reactivate(String newTokenHash) {
        this.active = true;
        this.tokenHash = newTokenHash;
        this.tokenIssuedAt = Instant.now();
    }

    /** Replaces the token of an active table, for example after a QR label is lost or copied. */
    public void rotate(String newTokenHash) {
        this.tokenHash = newTokenHash;
        this.tokenIssuedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getLabel() {
        return label;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getTokenIssuedAt() {
        return tokenIssuedAt;
    }
}
