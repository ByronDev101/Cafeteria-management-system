package ke.ac.kca.cafeteria.finance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByOrder_Id(Long orderId);

    List<PaymentRecord> findByOrder_IdIn(Collection<Long> orderIds);

    /** Records in one status with their orders, oldest change first. Always bounded by the page size. */
    @Query("select p from PaymentRecord p join fetch p.order o where p.statusCode = :status "
            + "order by p.updatedAt asc, p.id asc")
    List<PaymentRecord> findByStatusWithOrder(@Param("status") String status, Pageable pageable);

    long countByStatusCode(String statusCode);

    /** One row per status: the status, how many records have it, and when one last changed. Used for board polling. */
    @Query("select p.statusCode, count(p), max(p.updatedAt) from PaymentRecord p group by p.statusCode")
    List<Object[]> statusSummary();
}
