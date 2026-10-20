package ke.ac.kca.cafeteria.ordering;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Ownership is part of the lookup: another student's order simply is not found. */
    Optional<CustomerOrder> findByPublicIdAndStudent_Username(String publicId, String username);

    Optional<CustomerOrder> findByStudent_UsernameAndIdempotencyKey(String username, String idempotencyKey);

    Page<CustomerOrder> findByStudent_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    List<CustomerOrder> findTop5ByStudent_UsernameAndStatusCodeInOrderByCreatedAtDesc(String username,
                                                                                  Collection<String> statuses);

    // ---- staff board ----

    Optional<CustomerOrder> findByPublicId(String publicId);

    /** Oldest first, so the order waiting longest is at the top. Always bounded by the page size. */
    List<CustomerOrder> findByStatusCodeInOrderByCreatedAtAsc(Collection<String> statusCodes, Pageable pageable);

    /** Finished orders changed since the given moment, newest first. */
    List<CustomerOrder> findByStatusCodeInAndUpdatedAtAfterOrderByUpdatedAtDesc(Collection<String> statusCodes,
                                                                                Instant since, Pageable pageable);

    /** One row per status: the status, how many orders have it, and when one last changed. Used for counts and polling. */
    @Query("select c.statusCode, count(c), max(c.updatedAt) from CustomerOrder c group by c.statusCode")
    List<Object[]> statusSummary();

    boolean existsByOrderNumber(String orderNumber);
}
