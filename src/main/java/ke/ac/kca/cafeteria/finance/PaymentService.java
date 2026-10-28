package ke.ac.kca.cafeteria.finance;

import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.CustomerOrderRepository;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.StaffGuard;
import ke.ac.kca.cafeteria.reporting.AuditService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offline payment records and refunds (FR-023 to FR-026). Staff write down what they receive and hand back; the
 * system never touches real money, and every record is labelled as a prototype record.
 *
 * <p>The record opens when an order is placed and follows the order: it is voided if the order is cancelled before
 * payment, and becomes a refund due if the order is cancelled after payment. Marking a payment received or a refund
 * paid is for staff and managers only, checked here as well as in the URL rules. Every step is written to the audit
 * log in the same transaction, and a step that is not allowed leaves the record exactly as it was.
 */
@Service
public class PaymentService {

    public static final String OPENED = "PAYMENT_OPENED";
    public static final String VERIFIED = "PAYMENT_VERIFIED";
    public static final String VOIDED = "PAYMENT_VOIDED";
    public static final String REFUND_DUE = "PAYMENT_REFUND_DUE";
    public static final String REFUNDED = "PAYMENT_REFUNDED";
    public static final int REFERENCE_MAX = 40;

    private static final String TARGET = "PAYMENT";
    private static final String SUCCESS = "SUCCESS";
    private static final String PROTOTYPE = "prototype record, no real money";
    private static final String NOT_FOUND = "That order was not found.";

    private final PaymentRecordRepository payments;
    private final CustomerOrderRepository orders;
    private final AuditService audit;

    public PaymentService(PaymentRecordRepository payments, CustomerOrderRepository orders, AuditService audit) {
        this.payments = payments;
        this.orders = orders;
        this.audit = audit;
    }

    // ---- following the order ----

    /** Opens the payment record for a newly placed order. If one already exists it is returned unchanged. */
    @Transactional
    public PaymentRecord openFor(CustomerOrder order) {
        Optional<PaymentRecord> existing = payments.findByOrder_Id(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        PaymentRecord record = new PaymentRecord(order);
        payments.save(record);
        audit.record(order.getStudent().getUsername(), OPENED, TARGET, record.getPublicId(), SUCCESS,
                "order=" + order.getOrderNumber() + "; method=" + record.getMethodCode()
                        + "; amount=" + record.getAmount().toPlainString() + "; " + PROTOTYPE);
        return record;
    }

    /**
     * Called when an order is cancelled, by the student or by staff. A payment not yet received is voided; one that
     * was received becomes a refund due. A payment already voided or on its way back is left alone, so calling this
     * twice does no harm.
     */
    @Transactional
    public void settleOnCancellation(String actor, CustomerOrder order) {
        Optional<PaymentRecord> found = payments.findByOrder_Id(order.getId());
        if (found.isEmpty()) {
            return;
        }
        PaymentRecord record = found.get();
        PaymentStatus before = record.getStatus();
        if (before == PaymentStatus.PENDING) {
            record.markVoided();
            audit.record(actor, VOIDED, TARGET, record.getPublicId(), SUCCESS,
                    "order=" + order.getOrderNumber() + "; " + before + " -> " + record.getStatus()
                            + "; order cancelled before payment; " + PROTOTYPE);
        } else if (before == PaymentStatus.VERIFIED && record.getPaymentMethod() == PaymentMethod.CREDIT) {
            // Nobody hands cash back for a credit charge: the ledger reverses it, so the record closes straight away.
            record.markRefundDue();
            record.markRefunded();
            audit.record(actor, REFUNDED, TARGET, record.getPublicId(), SUCCESS,
                    "order=" + order.getOrderNumber() + "; " + before + " -> " + record.getStatus()
                            + "; amount=" + record.getAmount().toPlainString()
                            + "; credit charge reversed in the ledger; " + PROTOTYPE);
        } else if (before == PaymentStatus.VERIFIED) {
            record.markRefundDue();
            audit.record(actor, REFUND_DUE, TARGET, record.getPublicId(), SUCCESS,
                    "order=" + order.getOrderNumber() + "; " + before + " -> " + record.getStatus()
                            + "; amount=" + record.getAmount().toPlainString()
                            + "; order cancelled after payment; " + PROTOTYPE);
        }
    }

    /**
     * Called when staff accept an order paid with credit: the credit has just been charged, so the payment record is
     * marked received. Does nothing for other orders or for a record that is not awaiting payment.
     */
    @Transactional
    public void receiveByCredit(String actor, CustomerOrder order) {
        if (order.getPaymentMethod() != PaymentMethod.CREDIT) {
            return;
        }
        Optional<PaymentRecord> found = payments.findByOrder_Id(order.getId());
        if (found.isEmpty() || found.get().getStatus() != PaymentStatus.PENDING) {
            return;
        }
        PaymentRecord record = found.get();
        record.markReceived(actor, null);
        audit.record(actor, VERIFIED, TARGET, record.getPublicId(), SUCCESS,
                "order=" + order.getOrderNumber() + "; PENDING -> " + record.getStatus()
                        + "; method=" + record.getMethodCode() + "; amount=" + record.getAmount().toPlainString()
                        + "; charged to cafeteria credit; " + PROTOTYPE);
    }

    // ---- staff actions ----

    /** Staff write down that the payment for an order has been received. The reference is optional. */
    @Transactional
    public PaymentResult verify(String actor, String orderPublicId, String reference) {
        StaffGuard.require();
        CustomerOrder order = findOrder(orderPublicId);
        PaymentRecord record = recordFor(order);
        if (record.getPaymentMethod() == PaymentMethod.CREDIT) {
            throw new OrderException("Order " + order.getOrderNumber()
                    + " is paid with cafeteria credit, which is charged automatically when the order is accepted.");
        }
        String cleanReference = cleanReference(reference);

        PaymentStatus before = record.getStatus();
        if (!before.canMoveTo(PaymentStatus.VERIFIED)) {
            throw new OrderException(cannotReceive(order, before));
        }
        record.markReceived(actor, cleanReference);
        audit.record(actor, VERIFIED, TARGET, record.getPublicId(), SUCCESS,
                "order=" + order.getOrderNumber() + "; " + before + " -> " + record.getStatus()
                        + "; method=" + record.getMethodCode() + "; amount=" + record.getAmount().toPlainString()
                        + (cleanReference == null ? "" : "; reference=" + cleanReference) + "; " + PROTOTYPE);
        return new PaymentResult(order.getOrderNumber(), record.getStatus());
    }

    /** Staff write down that a refund has been handed back. */
    @Transactional
    public PaymentResult markRefunded(String actor, String orderPublicId) {
        StaffGuard.require();
        CustomerOrder order = findOrder(orderPublicId);
        PaymentRecord record = recordFor(order);

        PaymentStatus before = record.getStatus();
        if (!before.canMoveTo(PaymentStatus.REFUNDED)) {
            throw new OrderException(before == PaymentStatus.REFUNDED
                    ? "The refund for order " + order.getOrderNumber() + " was already marked as paid."
                    : "Order " + order.getOrderNumber() + " has no refund due.");
        }
        record.markRefunded();
        audit.record(actor, REFUNDED, TARGET, record.getPublicId(), SUCCESS,
                "order=" + order.getOrderNumber() + "; " + before + " -> " + record.getStatus()
                        + "; amount=" + record.getAmount().toPlainString() + "; " + PROTOTYPE);
        return new PaymentResult(order.getOrderNumber(), record.getStatus());
    }

    // ---- reads for the screens ----

    /** The payment view for each order that has a record, keyed by the order's database id. */
    @Transactional(readOnly = true)
    public Map<Long, PaymentView> viewsFor(Collection<CustomerOrder> found) {
        Map<Long, PaymentView> views = new HashMap<>();
        List<Long> ids = found.stream().map(CustomerOrder::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return views;
        }
        for (PaymentRecord record : payments.findByOrder_IdIn(ids)) {
            views.put(record.getOrder().getId(), PaymentView.of(record));
        }
        return views;
    }

    /** The payment view for one order, or null when it has no record. */
    @Transactional(readOnly = true)
    public PaymentView viewFor(CustomerOrder order) {
        return payments.findByOrder_Id(order.getId()).map(PaymentView::of).orElse(null);
    }

    /** Orders whose payment is waiting to be handed back, oldest first. Staff and managers only. */
    @Transactional(readOnly = true)
    public List<CustomerOrder> refundsDue(int limit) {
        StaffGuard.require();
        return payments.findByStatusWithOrder(PaymentStatus.REFUND_DUE.name(), PageRequest.of(0, limit)).stream()
                .map(PaymentRecord::getOrder)
                .toList();
    }

    @Transactional(readOnly = true)
    public long refundsDueCount() {
        StaffGuard.require();
        return payments.countByStatusCode(PaymentStatus.REFUND_DUE.name());
    }

    /**
     * A short text that changes whenever a payment is added or changes status, added to the board's refresh token so
     * a payment recorded on one screen shows on the others. It contains no order or payment details.
     */
    @Transactional(readOnly = true)
    public String pollPart() {
        StaffGuard.require();
        List<String> parts = new ArrayList<>();
        for (Object[] row : payments.statusSummary()) {
            Object lastChange = row[2];
            long stamp = lastChange instanceof Instant at ? at.toEpochMilli()
                    : (lastChange instanceof Date date ? date.getTime() : 0L);
            parts.add("PAY-" + row[0] + ":" + row[1] + ":" + stamp);
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }

    // ---- helpers ----

    private CustomerOrder findOrder(String orderPublicId) {
        return orders.findByPublicId(orderPublicId).orElseThrow(() -> new OrderException(NOT_FOUND));
    }

    private PaymentRecord recordFor(CustomerOrder order) {
        return payments.findByOrder_Id(order.getId())
                .orElseThrow(() -> new OrderException("No payment record was found for order "
                        + order.getOrderNumber() + "."));
    }

    /** Trims, drops control characters and extra spaces. Blank becomes null. Over 40 characters is refused. */
    static String cleanReference(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return null;
        }
        if (clean.length() > REFERENCE_MAX) {
            throw new OrderException("The payment reference can be at most " + REFERENCE_MAX + " characters.");
        }
        return clean;
    }

    private static String cannotReceive(CustomerOrder order, PaymentStatus status) {
        String number = order.getOrderNumber();
        return switch (status) {
            case VERIFIED -> "The payment for order " + number + " was already marked as received.";
            case VOIDED -> "Order " + number + " was cancelled before payment, so there is no payment to receive.";
            default -> "Order " + number + " was cancelled, so its payment can no longer be marked as received.";
        };
    }
}
