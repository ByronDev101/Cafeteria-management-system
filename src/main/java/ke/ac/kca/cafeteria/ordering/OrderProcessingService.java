package ke.ac.kca.cafeteria.ordering;

import ke.ac.kca.cafeteria.finance.PaymentService;
import ke.ac.kca.cafeteria.finance.PaymentView;
import ke.ac.kca.cafeteria.menu.PriceParser;
import ke.ac.kca.cafeteria.reporting.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The staff side of orders (FR-019 to FR-021): the queue, and the status changes that move an order along.
 *
 * <p>Every method first checks that the signed-in person is staff or a manager. A change is allowed only along the
 * arrows in {@link OrderStatus}; an illegal or stale request is refused and leaves the order exactly as it was. Each
 * change records who made it and when, and is written to the audit log in the same transaction.
 */
@Service
public class OrderProcessingService {

    static final String STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    private static final String TARGET = "ORDER";
    private static final String SUCCESS = "SUCCESS";
    private static final int BOARD_LIMIT = 100;
    private static final int REASON_MIN = 3;
    private static final int REASON_MAX = 255;

    private final CustomerOrderRepository orders;
    private final AuditService audit;
    private final PaymentService payments;
    private final ZoneId zone;
    private final DateTimeFormatter timeFormat;

    public OrderProcessingService(CustomerOrderRepository orders, AuditService audit, PaymentService payments,
                                  @Value("${app.display-timezone:Africa/Nairobi}") String zoneId) {
        this.orders = orders;
        this.audit = audit;
        this.payments = payments;
        this.zone = ZoneId.of(zoneId);
        this.timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
    }

    // ---- the board ----

    @Transactional(readOnly = true)
    public List<QueueOrder> queue(QueueFilter filter) {
        StaffGuard.require();
        List<CustomerOrder> found;
        if (filter.isRefundView()) {
            found = payments.refundsDue(BOARD_LIMIT);
        } else if (filter.isFinishedView()) {
            Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();
            found = orders.findByStatusCodeInAndUpdatedAtAfterOrderByUpdatedAtDesc(
                    filter.statusCodes(), startOfDay, PageRequest.of(0, BOARD_LIMIT));
        } else {
            found = orders.findByStatusCodeInOrderByCreatedAtAsc(filter.statusCodes(), PageRequest.of(0, BOARD_LIMIT));
        }
        Instant now = Instant.now();
        Map<Long, PaymentView> views = payments.viewsFor(found);
        return found.stream().map(order -> toQueueOrder(order, now, views)).toList();
    }

    /** Numbers for the tabs: how many orders are new, in the kitchen, ready, and active overall. */
    @Transactional(readOnly = true)
    public Map<QueueFilter, Long> counts() {
        StaffGuard.require();
        Map<OrderStatus, Long> byStatus = countsByStatus();
        Map<QueueFilter, Long> counts = new EnumMap<>(QueueFilter.class);
        for (QueueFilter filter : QueueFilter.values()) {
            if (filter.isRefundView()) {
                counts.put(filter, payments.refundsDueCount());
            } else if (!filter.isFinishedView()) {
                counts.put(filter, filter.statuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum());
            }
        }
        return counts;
    }

    /**
     * A short text that changes whenever any order is added or changes status. The board asks for it every few
     * seconds and reloads itself only when it is different. It contains no order details.
     */
    @Transactional(readOnly = true)
    public String pollToken() {
        StaffGuard.require();
        List<String> parts = new ArrayList<>();
        for (Object[] row : orders.statusSummary()) {
            Object lastChange = row[2];
            long stamp = lastChange instanceof Instant at ? at.toEpochMilli()
                    : (lastChange instanceof java.util.Date date ? date.getTime() : 0L);
            parts.add(row[0] + ":" + row[1] + ":" + stamp);
        }
        parts.sort(String::compareTo);
        String orderPart = String.join("|", parts);
        String paymentPart = payments.pollPart();
        return paymentPart == null || paymentPart.isEmpty() ? orderPart : orderPart + "#" + paymentPart;
    }

    // ---- changing status ----

    /**
     * Moves an order to a new status. {@code expectedVersion} is the version on the staff member's screen; if the
     * order has changed since, the request is refused so nobody acts on out-of-date information.
     */
    @Transactional
    public TransitionResult transition(String actor, String publicId, String toCode, String reason,
                                       Long expectedVersion) {
        StaffGuard.require();
        OrderStatus next = parseStatus(toCode);
        CustomerOrder order = orders.findByPublicId(publicId)
                .orElseThrow(() -> new OrderException("That order was not found."));

        if (expectedVersion != null && order.getVersion() != expectedVersion) {
            throw new OrderException("Order " + order.getOrderNumber() + " was changed by someone else. "
                    + "The board has been refreshed. Please check it and try again.");
        }
        OrderStatus current = order.getStatus();
        if (!current.canMoveTo(next)) {
            throw new OrderException("Order " + order.getOrderNumber() + " is " + current.getLabel().toLowerCase()
                    + " and cannot be moved to " + next.getLabel().toLowerCase() + ".");
        }

        String cleanReason = null;
        if (next == OrderStatus.CANCELLED) {
            cleanReason = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
            if (cleanReason.length() < REASON_MIN || cleanReason.length() > REASON_MAX) {
                throw new OrderException("Give a reason for cancelling, between " + REASON_MIN + " and "
                        + REASON_MAX + " characters.");
            }
        }

        // Slice B: when cafeteria credit is offered, cancelling a credit-funded order also reverses its credit here.
        order.moveTo(next, actor, cleanReason);
        if (next == OrderStatus.CANCELLED) {
            payments.settleOnCancellation(actor, order);
        }
        audit.record(actor, STATUS_CHANGED, TARGET, order.getPublicId(), SUCCESS,
                "number=" + order.getOrderNumber() + "; " + current.name() + " -> " + next.name()
                        + (cleanReason == null ? "" : "; reason=" + cleanReason));
        return new TransitionResult(order.getOrderNumber(), next);
    }

    // ---- helpers ----

    private Map<OrderStatus, Long> countsByStatus() {
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orders.statusSummary()) {
            byStatus.put(OrderStatus.valueOf(String.valueOf(row[0])), ((Number) row[1]).longValue());
        }
        return byStatus;
    }

    private static OrderStatus parseStatus(String code) {
        if (code != null) {
            for (OrderStatus status : OrderStatus.values()) {
                if (status.name().equals(code.trim())) {
                    return status;
                }
            }
        }
        throw new OrderException("Choose a valid status.");
    }

    private QueueOrder toQueueOrder(CustomerOrder order, Instant now, Map<Long, PaymentView> views) {
        OrderStatus status = order.getStatus();
        PaymentView payment = order.getId() == null ? null : views.get(order.getId());
        List<String> lines = order.getItems().stream()
                .map(item -> item.getQuantity() + " x " + item.getItemName())
                .toList();
        OrderStatus next = forwardStep(status);
        return new QueueOrder(order.getPublicId(), order.getOrderNumber(), status.name(), status.getLabel(),
                order.getTableLabel(), order.getStudent().getDisplayName(), timeFormat.format(order.getCreatedAt()),
                waitText(order.getCreatedAt(), now), lines, PriceParser.format(order.getTotal()),
                order.getPaymentMethod().getLabel(), order.getVersion(),
                next == null ? null : next.name(), next == null ? null : actionLabel(next),
                status.canMoveTo(OrderStatus.CANCELLED), cancelNote(order, status),
                payment == null ? null : payment.getStatusCode(), payment == null ? null : payment.getStatusLabel(),
                payment == null ? null : payment.getReference(), payment != null && payment.isCanVerify(),
                payment != null && payment.isCanMarkRefunded(), payment != null && payment.isAskReference());
    }

    /** The single "next" button for each status, or null once an order is finished. */
    static OrderStatus forwardStep(OrderStatus status) {
        return switch (status) {
            case SUBMITTED -> OrderStatus.ACCEPTED;
            case ACCEPTED -> OrderStatus.PREPARING;
            case PREPARING -> OrderStatus.READY;
            case READY -> OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> null;
        };
    }

    static String actionLabel(OrderStatus next) {
        return switch (next) {
            case ACCEPTED -> "Accept order";
            case PREPARING -> "Start preparing";
            case READY -> "Mark ready";
            case COMPLETED -> "Served: complete";
            default -> next.getLabel();
        };
    }

    /** How long an order has been waiting, in words: "just now", "7 min ago", "1 h 5 min ago". */
    static String waitText(Instant created, Instant now) {
        long minutes = Math.max(0, Duration.between(created, now).toMinutes());
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return hours + " h" + (rest == 0 ? "" : " " + rest + " min") + " ago";
    }

    private static String cancelNote(CustomerOrder order, OrderStatus status) {
        if (status != OrderStatus.CANCELLED) {
            return null;
        }
        for (OrderStatusHistory entry : order.getHistory()) {
            if (entry.getToStatus() == OrderStatus.CANCELLED) {
                boolean byStudent = entry.getActorUsername().equals(order.getStudent().getUsername());
                String reason = entry.getReason();
                return byStudent ? "Cancelled by the student"
                        : "Cancelled by staff" + (reason == null ? "" : ": " + reason);
            }
        }
        return "Cancelled";
    }
}
