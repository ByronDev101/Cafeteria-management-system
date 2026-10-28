package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.identity.AppUser;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    Optional<CreditAccount> findByStudent_Id(Long studentId);

    /**
     * The account locked for the rest of the transaction, so two orders placed at the same moment by one student
     * are checked against the limit one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.student.id = :studentId")
    Optional<CreditAccount> findForUpdate(@Param("studentId") Long studentId);

    @Query("select a from CreditAccount a where a.student.id in :ids")
    List<CreditAccount> findByStudentIds(@Param("ids") Collection<Long> ids);

    /** Students who already have a credit account, by name. */
    @Query("select a.student from CreditAccount a order by a.student.displayName, a.id")
    List<AppUser> studentsWithAccounts(Pageable pageable);

    /** Active students whose name or username contains the text. The pattern is lower case with % around it. */
    @Query("select u from AppUser u join u.roles r where r.name = 'STUDENT' and u.active = true "
            + "and (lower(u.username) like :pattern or lower(u.displayName) like :pattern) "
            + "order by u.displayName, u.id")
    List<AppUser> searchStudents(@Param("pattern") String pattern, Pageable pageable);
}
