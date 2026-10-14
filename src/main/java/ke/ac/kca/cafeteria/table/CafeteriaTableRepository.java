package ke.ac.kca.cafeteria.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CafeteriaTableRepository extends JpaRepository<CafeteriaTable, Long> {

    Optional<CafeteriaTable> findByPublicId(String publicId);

    Optional<CafeteriaTable> findByTokenHash(String tokenHash);

    List<CafeteriaTable> findAllByOrderByLabelAsc();

    boolean existsByLabelIgnoreCase(String label);

    boolean existsByLabelIgnoreCaseAndIdNot(String label, Long id);
}
