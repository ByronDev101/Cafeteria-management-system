package ke.ac.kca.cafeteria.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.PlacedOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SRS FR-028 and the MVP checklist: simultaneous requests can never take a student over their credit limit, and a
 * double tap never places or holds twice. These run many real transactions at the same moment against MySQL.
 */
class CreditConcurrencyIT extends DatabaseIT {

    /** Runs the same job on {@code threads} threads released at the same instant and returns each outcome. */
    private List<String> runTogether(int threads, Callable<String> job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return job.call();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "threads did not start");
            go.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(90, TimeUnit.SECONDS));   // an unexpected exception fails the test here
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long occurrences(List<String> outcomes, String what) {
        return outcomes.stream().filter(what::equals).count();
    }

    @Test
    void simultaneousOrdersCanNeverTakeAStudentOverTheirLimit() throws Exception {
        AppUser amina = student("amina");
        setCreditLimit(amina, "350");                         // each order is KSh 100.00, so exactly three fit

        List<String> outcomes = runTogether(8, () -> {
            try {
                orderService.place("amina", Map.of(tea.getPublicId(), 2), table.getPublicId(), PaymentMethod.CREDIT,
                        UUID.randomUUID().toString());
                return "placed";
            } catch (OrderException e) {
                return "refused";
            }
        });

        assertEquals(3, occurrences(outcomes, "placed"), outcomes.toString());
        assertEquals(5, occurrences(outcomes, "refused"), outcomes.toString());
        assertEquals(3, count("orders"));
        assertEquals(3, count("order_items"));
        assertEquals(3, count("payment_records"));
        assertEquals(3, count("credit_entries"));
        assertEquals(0, new BigDecimal("300.00").compareTo(sum("amount", "credit_entries")));
        assertEquals("KSh 50.00", balanceOf("amina").getAvailableText());
        assertEquals("KSh 300.00", balanceOf("amina").getHeldText());
    }

    @Test
    void ordersFromDifferentStudentsDoNotBlockEachOther() throws Exception {
        for (String name : List.of("amina", "brian", "carol", "dan")) {
            setCreditLimit(student(name), "100");             // each can place exactly one KSh 100.00 order
        }
        List<String> names = List.of("amina", "brian", "carol", "dan", "amina", "brian", "carol", "dan");
        List<String> outcomes = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(names.size());
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (String name : names) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        orderService.place(name, Map.of(tea.getPublicId(), 2), table.getPublicId(),
                                PaymentMethod.CREDIT, UUID.randomUUID().toString());
                        return "placed";
                    } catch (OrderException e) {
                        return "refused";
                    }
                }));
            }
            go.countDown();
            for (Future<String> future : futures) {
                outcomes.add(future.get(90, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(4, occurrences(outcomes, "placed"), outcomes.toString());
        assertEquals(4, occurrences(outcomes, "refused"), outcomes.toString());
        assertEquals(4, count("orders"));
        for (String name : List.of("amina", "brian", "carol", "dan")) {
            assertEquals("KSh 0.00", balanceOf(name).getAvailableText(), name);
        }
    }

    @Test
    void simultaneousPaymentsCanNeverTakeMoreThanIsOwed() throws Exception {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");
        PlacedOrder placed = place("amina", PaymentMethod.CREDIT, 2);                 // KSh 100.00
        signInAs("sam", "ROLE_STAFF");
        processing.transition("sam", placed.publicId(), "ACCEPTED", null, null);
        SecurityContextHolder.clearContext();

        List<String> outcomes = runTogether(4, () -> {
            signInAs("pat", "ROLE_MANAGER");                  // each thread has its own security context
            try {
                creditService.settle("pat", amina.getPublicId(), "60", null);
                return "paid";
            } catch (OrderException e) {
                return "refused";
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        assertEquals(1, occurrences(outcomes, "paid"), outcomes.toString());
        assertEquals(3, occurrences(outcomes, "refused"), outcomes.toString());
        BigDecimal settled = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from credit_entries where entry_type = 'SETTLEMENT'", BigDecimal.class);
        assertEquals(0, new BigDecimal("60.00").compareTo(settled));
        assertEquals("KSh 40.00", balanceOf("amina").getOwedText());
    }

    @Test
    void aDoubleTapPlacesOneOrderAndHoldsCreditOnce() throws Exception {
        AppUser amina = student("amina");
        setCreditLimit(amina, "1000");
        String sameKey = "double-tap-key-0001";

        List<String> outcomes = runTogether(5, () -> {
            try {
                orderService.place("amina", Map.of(tea.getPublicId(), 2), table.getPublicId(), PaymentMethod.CREDIT,
                        sameKey);
                return "ok";
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                return "lost the race";                       // the controller shows the winner's order instead
            }
        });

        assertTrue(occurrences(outcomes, "ok") >= 1, outcomes.toString());
        assertEquals(1, count("orders"), outcomes.toString());
        assertEquals(1, count("payment_records"));
        assertEquals(1, count("credit_entries"));
        assertEquals("KSh 100.00", balanceOf("amina").getHeldText());
    }
}
