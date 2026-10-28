package ke.ac.kca.cafeteria.credit;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** The ledger. Deliberately not a JpaRepository: lines can be added and read, never changed or deleted. */
public interface CreditEntryRepository extends Repository<CreditEntry, Long> {

    CreditEntry save(CreditEntry entry);

    /** One row per line type: the type and the sum of its amounts, for one student. */
    @Query("select e.entryTypeCode, sum(e.amount) from CreditEntry e where e.student.id = :studentId "
            + "group by e.entryTypeCode")
    List<Object[]> totalsForStudent(@Param("studentId") Long studentId);

    /** One row per line type: the type and the sum of its amounts, for one order. */
    @Query("select e.entryTypeCode, sum(e.amount) from CreditEntry e where e.order.id = :orderId "
            + "group by e.entryTypeCode")
    List<Object[]> totalsForOrder(@Param("orderId") Long orderId);

    /** Rows of student id, line type and summed amount, for a page of students. */
    @Query("select e.student.id, e.entryTypeCode, sum(e.amount) from CreditEntry e where e.student.id in :ids "
            + "group by e.student.id, e.entryTypeCode")
    List<Object[]> totalsForStudents(@Param("ids") Collection<Long> ids);

    /** A student's newest lines first, with the order they belong to. Always bounded by the page size. */
    @Query("select e from CreditEntry e left join fetch e.order where e.student.username = :username "
            + "order by e.id desc")
    List<CreditEntry> recentForStudent(@Param("username") String username, Pageable pageable);
}
