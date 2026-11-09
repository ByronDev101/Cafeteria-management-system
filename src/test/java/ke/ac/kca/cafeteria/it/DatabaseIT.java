package ke.ac.kca.cafeteria.it;

import ke.ac.kca.cafeteria.credit.CreditBalance;
import ke.ac.kca.cafeteria.credit.CreditService;
import ke.ac.kca.cafeteria.finance.PaymentService;
import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.identity.RoleRepository;
import ke.ac.kca.cafeteria.identity.UserRepository;
import ke.ac.kca.cafeteria.menu.MenuCategory;
import ke.ac.kca.cafeteria.menu.MenuCategoryRepository;
import ke.ac.kca.cafeteria.menu.MenuItem;
import ke.ac.kca.cafeteria.menu.MenuItemRepository;
import ke.ac.kca.cafeteria.ordering.OrderProcessingService;
import ke.ac.kca.cafeteria.ordering.OrderService;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.PlacedOrder;
import ke.ac.kca.cafeteria.reporting.ReportService;
import ke.ac.kca.cafeteria.table.CafeteriaTable;
import ke.ac.kca.cafeteria.table.CafeteriaTableRepository;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * The common set-up for tests that run against a real MySQL schema. Every test starts from empty data tables with one
 * menu item (Tea, KSh 50.00) and one table (Table 7). Services are the real ones, with the real transactions.
 */
@SpringBootTest
@ActiveProfiles("it")
public abstract class DatabaseIT {

    /** Data tables, emptied before every test. Roles and the migration history are kept. */
    private static final List<String> DATA_TABLES = List.of(
            "credit_entries", "credit_accounts", "payment_records", "order_status_history", "order_items", "orders",
            "audit_events", "menu_item_images", "menu_items", "menu_categories", "cafeteria_tables",
            "student_profiles", "user_roles", "users");

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected RoleRepository roles;
    @Autowired protected MenuCategoryRepository categories;
    @Autowired protected MenuItemRepository menuItems;
    @Autowired protected CafeteriaTableRepository tableRepository;
    @Autowired protected OrderService orderService;
    @Autowired protected OrderProcessingService processing;
    @Autowired protected PaymentService paymentService;
    @Autowired protected CreditService creditService;
    @Autowired protected ReportService reportService;

    protected MenuItem tea;
    protected CafeteriaTable table;

    @BeforeEach
    void resetDatabase() {
        SecurityContextHolder.clearContext();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String name : DATA_TABLES) {
                    statement.execute("TRUNCATE TABLE " + name);
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
        MenuCategory category = new MenuCategory("Drinks", "Hot and cold drinks", 1);
        category.setActive(true);
        category = categories.save(category);
        MenuItem item = new MenuItem(category, "Tea", "Hot tea", new BigDecimal("50.00"));
        item.setActive(true);
        item.setAvailable(true);
        tea = menuItems.save(item);
        table = tableRepository.save(new CafeteriaTable("Table 7", "a".repeat(64)));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ---- who is signed in ----

    protected static void signInAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(role))));
    }

    // ---- fixtures ----

    /** A student account. The hash is a fixed placeholder, not a real password: these tests never sign in. */
    protected AppUser student(String username) {
        AppUser user = new AppUser(username, "$2a$10$placeholderplaceholderplaceholderplaceholderplaceholde",
                username + " Student");
        user.addRole(roles.findByName("STUDENT").orElseThrow());
        return users.save(user);
    }

    /** Places an order of {@code teas} cups of tea (KSh 50.00 each) at the standard table. */
    protected PlacedOrder place(String username, PaymentMethod method, int teas) {
        return orderService.place(username, Map.of(tea.getPublicId(), teas), table.getPublicId(), method,
                UUID.randomUUID().toString());
    }

    protected void setCreditLimit(AppUser student, String limit) {
        signInAs("pat", "ROLE_MANAGER");
        creditService.setLimit("pat", student.getPublicId(), limit);
        SecurityContextHolder.clearContext();
    }

    // ---- reading the database directly ----

    protected long count(String tableName) {
        Long count = jdbc.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    protected BigDecimal sum(String column, String tableName) {
        BigDecimal total = jdbc.queryForObject("select coalesce(sum(" + column + "), 0) from " + tableName,
                BigDecimal.class);
        return total == null ? BigDecimal.ZERO : total;
    }

    protected String paymentStatus(String orderPublicId) {
        return jdbc.queryForObject("select p.status from payment_records p join orders o on o.id = p.order_id "
                + "where o.public_id = ?", String.class, orderPublicId);
    }

    protected String orderStatus(String orderPublicId) {
        return jdbc.queryForObject("select status from orders where public_id = ?", String.class, orderPublicId);
    }

    protected long auditCount(String action) {
        Long count = jdbc.queryForObject("select count(*) from audit_events where action = ?", Long.class, action);
        return count == null ? 0 : count;
    }

    /** Moves an order's creation time, as UTC, the way Hibernate stores it. */
    protected void backdate(String orderPublicId, Instant createdAt) {
        jdbc.update("update orders set created_at = ? where public_id = ?",
                LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC), orderPublicId);
    }

    protected CreditBalance balanceOf(String username) {
        return creditService.balanceOf(username);
    }
}
