package ke.ac.kca.cafeteria.reporting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Deliberately extends Repository, not JpaRepository: only save and read, no update or delete methods. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
