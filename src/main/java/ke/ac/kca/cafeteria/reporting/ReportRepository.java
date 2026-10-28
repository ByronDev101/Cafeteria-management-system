package ke.ac.kca.cafeteria.reporting;

import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries for the manager reports. Nothing here changes data. */
public interface ReportRepository extends Repository<CustomerOrder, Long> {

    /** One row per order status: the status, how many orders, and their combined total. */
    @Query("select o.statusCode, count(o), sum(o.total) from CustomerOrder o "
            + "where o.createdAt >= :start and o.createdAt < :end group by o.statusCode")
    List<Object[]> orderTotalsByStatus(@Param("start") Instant start, @Param("end") Instant end);

    /** One row per payment status and method: the status, the method, how many records, and their combined amount. */
    @Query("select p.statusCode, p.methodCode, count(p), sum(p.amount) from PaymentRecord p "
            + "where p.order.createdAt >= :start and p.order.createdAt < :end group by p.statusCode, p.methodCode")
    List<Object[]> paymentTotals(@Param("start") Instant start, @Param("end") Instant end);

    /** Orders in range, newest first. The two text patterns are already lower case with % around them. */
    @Query("select o from CustomerOrder o where o.createdAt >= :start and o.createdAt < :end "
            + "and o.statusCode in :statuses and lower(o.orderNumber) like :number and lower(o.tableLabel) like :table "
            + "order by o.createdAt desc, o.id desc")
    List<CustomerOrder> search(@Param("start") Instant start, @Param("end") Instant end,
                               @Param("statuses") Collection<String> statuses, @Param("number") String number,
                               @Param("table") String table, Pageable pageable);
}
