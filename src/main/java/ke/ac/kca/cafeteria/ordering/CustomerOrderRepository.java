package ke.ac.kca.cafeteria.ordering;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Ownership is part of the lookup: another student's order simply is not found. */
    Optional<CustomerOrder> findByPublicIdAndStudent_Username(String publicId, String username);

    Optional<CustomerOrder> findByStudent_UsernameAndIdempotencyKey(String username, String idempotencyKey);

    Page<CustomerOrder> findByStudent_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    List<CustomerOrder> findTop5ByStudent_UsernameAndStatusCodeInOrderByCreatedAtDesc(String username,
                                                                                  Collection<String> statuses);

    boolean existsByOrderNumber(String orderNumber);
}
