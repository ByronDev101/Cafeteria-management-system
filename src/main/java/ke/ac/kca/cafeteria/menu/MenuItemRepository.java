package ke.ac.kca.cafeteria.menu;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByPublicId(String publicId);

    List<MenuItem> findByCategoryOrderByNameAsc(MenuCategory category);

    boolean existsByCategoryAndNameIgnoreCase(MenuCategory category, String name);

    boolean existsByCategoryAndNameIgnoreCaseAndIdNot(MenuCategory category, String name, Long id);
}
