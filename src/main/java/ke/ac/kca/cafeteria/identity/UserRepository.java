package ke.ac.kca.cafeteria.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByPublicId(String publicId);

    boolean existsByUsername(String username);

    @Query("select count(u) from AppUser u join u.roles r where r.name = :roleName and u.active = true")
    long countActiveWithRole(@Param("roleName") String roleName);
}
