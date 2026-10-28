package ke.ac.kca.cafeteria.ordering;

import ke.ac.kca.cafeteria.finance.PaymentService;
import ke.ac.kca.cafeteria.finance.PaymentView;
import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.identity.UserRepository;
import ke.ac.kca.cafeteria.menu.MenuService;
import ke.ac.kca.cafeteria.menu.OrderableItem;
import ke.ac.kca.cafeteria.menu.PriceParser;
import ke.ac.kca.cafeteria.reporting.AuditService;
import ke.ac.kca.cafeteria.table.TableRef;
import ke.ac.kca.cafeteria.table.TableService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placing, viewing and cancelling orders (FR-014 to FR-022).
 *
 * <p>Rules this class holds on to: prices and names come from the menu at the moment of ordering and are copied
 * into the order; the table must be switched on; every student read goes through an ownership check, and another
 * student's order is reported as "not found" so nothing is revealed; status changes follow the allowed arrows and
 * are written to the history.
 */
@Service
public class OrderService {

    static final String PLACED = "ORDER_PLACED";
    static final String CANCELLED = "ORDER_CANCELLED";
    private static final String TARGET = "ORDER";
    private static final String SUCCESS = "SUCCESS";
    private static final int PAGE_SIZE = 10;
    private static final int NUMBER_ATTEMPTS = 10;
    private static final String NOT_FOUND = "That order was not found.";

    private final CustomerOrderRepository orders;
    private final UserRepository users;
    private final TableService tables;
    private final MenuService menu;
    private final AuditService audit;
    private final PaymentService payments;
    private final DateTimeFormatter timeFormat;

    public OrderService(CustomerOrderRepository orders, UserRepository users, TableService tables,
                        MenuService menu, AuditService audit, PaymentService payments,
                        @Value("${app.display-timezone:Africa/Nairobi}") String zoneId) {
        this.orders = orders;
        this.users = users;
        this.tables = tables;
        this.menu = menu;
        this.audit = audit;
        this.payments = payments;
        this.timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of(zoneId));
    }

    // ---- placing ----

    /**
     * Places an order from the cart's lines. If the same duplicate-submit key was already used by this student, the
     * earlier order is returned instead of creating a second one.
     */
    @Transactional
    public PlacedOrder place(String username, Map<String, Integer> lines, String tablePublicId,
                             PaymentMethod method, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CustomerOrder> earlier = orders.findByStudent_UsernameAndIdempotencyKey(username, idempotencyKey);
            if (earlier.isPresent()) {
                return new PlacedOrder(earlier.get().getPublicId(), earlier.get().getOrderNumber(), true);
            }
        }

        List<String> problems = new ArrayList<>();
        if (method == null || !PaymentMethod.offered().contains(method)) {
            problems.add("Choose how you will pay.");
        }
        if (lines == null || lines.isEmpty()) {
            problems.add("Your cart is empty.");
        }
        Optional<TableRef> table = tablePublicId == null ? Optional.empty() : tables.activeTable(tablePublicId);
        if (table.isEmpty()) {
            problems.add(tablePublicId == null
                    ? "Choose your table first: scan the QR code on your table or enter the table code."
                    : "Your table is no longer available. Scan the QR code on your table again.");
        }

        Map<String, OrderableItem> found = (lines == null || lines.isEmpty())
                ? Map.of() : menu.lookup(lines.keySet());
        if (lines != null) {
            for (Map.Entry<String, Integer> line : lines.entrySet()) {
                OrderableItem item = found.get(line.getKey());
                Integer quantity = line.getValue();
                if (item == null) {
                    problems.add("An item in your cart is no longer on the menu. Remove it to continue.");
                } else if (!item.orderable()) {
                    problems.add(item.name() + " is " + item.problem() + ". Remove it to continue.");
                } else if (quantity == null || quantity < 1 || quantity > Cart.MAX_QUANTITY) {
                    problems.add("Choose 1 to " + Cart.MAX_QUANTITY + " of " + item.name() + ".");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new OrderException(problems);
        }

        AppUser student = users.findByUsername(AppUser.normalize(username))
                .orElseThrow(() -> new OrderException("Your account was not found."));
        CustomerOrder order = new CustomerOrder(newOrderNumber(), student, table.get().id(), table.get().label(),
                method, blankToNull(idempotencyKey));
        for (Map.Entry<String, Integer> line : lines.entrySet()) {
            OrderableItem item = found.get(line.getKey());
            order.addItem(item.id(), item.name(), item.unitPrice(), line.getValue());
        }
        orders.save(order);
        payments.openFor(order);

        audit.record(student.getUsername(), PLACED, TARGET, order.getPublicId(), SUCCESS,
                "number=" + order.getOrderNumber() + "; table=" + order.getTableLabel()
                        + "; total=" + order.getTotal().toPlainString() + "; payment=" + method.name());
        return new PlacedOrder(order.getPublicId(), order.getOrderNumber(), false);
    }

    // ---- student reads (always checked for ownership) ----

    @Transactional(readOnly = true)
    public Page<OrderSummary> history(String username, int page) {
        return orders.findByStudent_UsernameOrderByCreatedAtDesc(username, PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(this::summary);
    }

    /** The student's orders that are still in progress, newest first, for the "live status" cards. */
    @Transactional(readOnly = true)
    public List<OrderSummary> activeOrders(String username) {
        List<String> active = OrderStatus.activeStatuses().stream().map(Enum::name).toList();
        return orders.findTop5ByStudent_UsernameAndStatusCodeInOrderByCreatedAtDesc(username, active).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderView detail(String username, String publicId) {
        return view(owned(username, publicId));
    }

    /** The current status code, for the page's quiet refresh. */
    @Transactional(readOnly = true)
    public OrderStatus statusOf(String username, String publicId) {
        return owned(username, publicId).getStatus();
    }

    @Transactional(readOnly = true)
    public Optional<PlacedOrder> findByKey(String username, String idempotencyKey) {
        return orders.findByStudent_UsernameAndIdempotencyKey(username, idempotencyKey)
                .map(order -> new PlacedOrder(order.getPublicId(), order.getOrderNumber(), true));
    }

    // ---- cancelling ----

    /** A student cancels their own order, only before staff accept it (FR-018). */
    @Transactional
    public void cancelOwn(String username, String publicId) {
        CustomerOrder order = owned(username, publicId);
        if (!order.getStatus().studentMayCancel()) {
            throw new OrderException("This order can no longer be cancelled here because it has been "
                    + order.getStatus().getLabel().toLowerCase() + ". Please ask a member of staff.");
        }
        // Slice B: when cafeteria credit is offered, release any credit held for this order here.
        order.moveTo(OrderStatus.CANCELLED, order.getStudent().getUsername(), "Cancelled by the student");
        payments.settleOnCancellation(order.getStudent().getUsername(), order);
        audit.record(order.getStudent().getUsername(), CANCELLED, TARGET, order.getPublicId(), SUCCESS,
                "number=" + order.getOrderNumber() + "; by the student");
    }

    // ---- helpers ----

    private CustomerOrder owned(String username, String publicId) {
        return orders.findByPublicIdAndStudent_Username(publicId, username)
                .orElseThrow(() -> new OrderException(NOT_FOUND));
    }

    private String newOrderNumber() {
        for (int attempt = 0; attempt < NUMBER_ATTEMPTS; attempt++) {
            String candidate = OrderNumber.generate();
            if (!orders.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not create a unique order number");
    }

    private OrderSummary summary(CustomerOrder order) {
        OrderStatus status = order.getStatus();
        String items = order.getItems().stream()
                .limit(3)
                .map(item -> item.getQuantity() + " x " + item.getItemName())
                .collect(Collectors.joining(", "));
        if (order.getItems().size() > 3) {
            items += " and " + (order.getItems().size() - 3) + " more";
        }
        return new OrderSummary(order.getPublicId(), order.getOrderNumber(), status.name(), status.getLabel(),
                status.isActive(), PriceParser.format(order.getTotal()), timeFormat.format(order.getCreatedAt()),
                order.getTableLabel(), items);
    }

    private OrderView view(CustomerOrder order) {
        OrderStatus current = order.getStatus();
        PaymentView payment = payments.viewFor(order);
        List<OrderLineView> lines = order.getItems().stream()
                .map(item -> new OrderLineView(item.getItemName(), item.getQuantity(),
                        PriceParser.format(item.getUnitPrice()), PriceParser.format(item.getLineTotal())))
                .toList();
        return new OrderView(order.getPublicId(), order.getOrderNumber(), current.name(), current.getLabel(),
                current.isTerminal(), current.studentMayCancel(), order.getTableLabel(),
                order.getPaymentMethod().getLabel(), PriceParser.format(order.getTotal()),
                timeFormat.format(order.getCreatedAt()), lines, steps(order, current), cancelNote(order, current),
                payment == null ? null : payment.getStatusCode(), payment == null ? null : payment.getStatusLabel(),
                payment == null ? null : payment.getStudentNote());
    }

    /** For a cancelled order: who cancelled it and why, worded for the student. */
    private static String cancelNote(CustomerOrder order, OrderStatus current) {
        if (current != OrderStatus.CANCELLED) {
            return null;
        }
        for (OrderStatusHistory entry : order.getHistory()) {
            if (entry.getToStatus() == OrderStatus.CANCELLED) {
                if (entry.getActorUsername().equals(order.getStudent().getUsername())) {
                    return "You cancelled this order.";
                }
                return "Cancelled by staff" + (entry.getReason() == null ? "." : ": " + entry.getReason());
            }
        }
        return null;
    }

    /** The progress tracker: which steps are done, which one is current, and when each was reached. */
    private List<OrderStepView> steps(CustomerOrder order, OrderStatus current) {
        Map<OrderStatus, Instant> reached = new EnumMap<>(OrderStatus.class);
        for (OrderStatusHistory entry : order.getHistory()) {
            reached.putIfAbsent(entry.getToStatus(), entry.getCreatedAt());
        }
        List<OrderStepView> steps = new ArrayList<>();
        if (current == OrderStatus.CANCELLED) {
            for (OrderStatus status : OrderStatus.PROGRESS) {
                if (reached.containsKey(status)) {
                    steps.add(new OrderStepView(status.getLabel(), "done", timeFormat.format(reached.get(status))));
                }
            }
            steps.add(new OrderStepView("Cancelled", "cancelled", timeFormat.format(reached.get(OrderStatus.CANCELLED))));
            return steps;
        }
        int position = OrderStatus.PROGRESS.indexOf(current);
        for (int i = 0; i < OrderStatus.PROGRESS.size(); i++) {
            OrderStatus status = OrderStatus.PROGRESS.get(i);
            String state = (i < position || current == OrderStatus.COMPLETED) ? "done"
                    : (i == position ? "current" : "upcoming");
            Instant at = reached.get(status);
            steps.add(new OrderStepView(status.getLabel(), state, at == null ? null : timeFormat.format(at)));
        }
        return steps;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
