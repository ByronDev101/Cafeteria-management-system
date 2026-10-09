package ke.ac.kca.cafeteria.menu;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    Optional<MenuCategory> findByPublicId(String publicId);

    List<MenuCategory> findAllByOrderByDisplayOrderAscNameAsc();

    List<MenuCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
