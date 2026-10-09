package ke.ac.kca.cafeteria.reporting;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads audit events (FR-037, NFR-11). record() joins the caller's transaction, so a change and
 * its audit entry are saved together or not at all. Never pass passwords, session IDs or tokens as details.
 */
@Service
public class AuditService {

    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_ACTIVATED = "USER_ACTIVATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String USER_ROLES_CHANGED = "USER_ROLES_CHANGED";
    public static final String USER_UNLOCKED = "USER_UNLOCKED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_CHANGE_FAILED = "PASSWORD_CHANGE_FAILED";

    private static final int PAGE_SIZE = 50;
    private static final int MAX_DETAILS = 500;

    private final AuditEventRepository events;
    private final DateTimeFormatter timeFormat;

    public AuditService(AuditEventRepository events,
                        @Value("${app.display-timezone:Africa/Nairobi}") String zoneId) {
        this.events = events;
        this.timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of(zoneId));
    }

    @Transactional
    public void record(String actor, String action, String targetType, String targetId,
                       String outcome, String details) {
        events.save(new AuditEvent(actor, action, targetType, targetId, outcome, truncate(details)));
    }

    @Transactional(readOnly = true)
    public Page<AuditRow> recent(int page) {
        return events.findAllByOrderByOccurredAtDesc(PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(e -> new AuditRow(
                        timeFormat.format(e.getOccurredAt()),
                        e.getActorUsername(),
                        e.getAction(),
                        e.getTargetType() + " " + e.getTargetId(),
                        e.getOutcome(),
                        e.getDetails()));
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_DETAILS) {
            return text;
        }
        return text.substring(0, MAX_DETAILS);
    }
}
