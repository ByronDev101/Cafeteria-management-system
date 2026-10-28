package ke.ac.kca.cafeteria.reporting;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The days a report covers, in campus time. Both end dates are included. With nothing chosen it is the last 7 days
 * ending today. A range that cannot be used (a date that is not a date, a start after the end, more than 366 days,
 * or a silly year) falls back to the last 7 days and carries a warning to show on the page, instead of failing.
 */
public final class ReportRange {

    public static final int DEFAULT_DAYS = 7;
    public static final int MAX_DAYS = 366;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final LocalDate from;
    private final LocalDate to;
    private final ZoneId zone;
    private final List<String> warnings;

    private ReportRange(LocalDate from, LocalDate to, ZoneId zone, List<String> warnings) {
        this.from = from;
        this.to = to;
        this.zone = zone;
        this.warnings = List.copyOf(warnings);
    }

    public static ReportRange parse(String fromText, String toText, ZoneId zone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        boolean noStart = fromText == null || fromText.isBlank();
        boolean noEnd = toText == null || toText.isBlank();
        if (noStart && noEnd) {
            return lastDays(today, zone, List.of());
        }

        LocalDate from = null;
        LocalDate to = null;
        try {
            if (!noStart) {
                from = LocalDate.parse(fromText.trim());
            }
            if (!noEnd) {
                to = LocalDate.parse(toText.trim());
            }
        } catch (DateTimeParseException e) {
            return fallback(today, zone, "Those dates were not valid, so the last " + DEFAULT_DAYS + " days are shown.");
        }
        if (!sensible(from) || !sensible(to)) {
            return fallback(today, zone, "Those dates were not valid, so the last " + DEFAULT_DAYS + " days are shown.");
        }
        if (to == null) {
            to = today;
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1);
        }
        if (from.isAfter(to)) {
            return fallback(today, zone,
                    "The start date was after the end date, so the last " + DEFAULT_DAYS + " days are shown.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            return fallback(today, zone,
                    "A report can cover at most " + MAX_DAYS + " days, so the last " + DEFAULT_DAYS + " days are shown.");
        }
        return new ReportRange(from, to, zone, List.of());
    }

    private static boolean sensible(LocalDate date) {
        return date == null || (date.getYear() >= MIN_YEAR && date.getYear() <= MAX_YEAR);
    }

    private static ReportRange fallback(LocalDate today, ZoneId zone, String warning) {
        return lastDays(today, zone, List.of(warning));
    }

    private static ReportRange lastDays(LocalDate today, ZoneId zone, List<String> warnings) {
        return new ReportRange(today.minusDays(DEFAULT_DAYS - 1), today, zone, warnings);
    }

    /** First day, included. */
    public LocalDate getFrom() {
        return from;
    }

    /** Last day, included. */
    public LocalDate getTo() {
        return to;
    }

    /** The first day as yyyy-MM-dd, for a date box. */
    public String getFromText() {
        return from.toString();
    }

    /** The last day as yyyy-MM-dd, for a date box. */
    public String getToText() {
        return to.toString();
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** The moment the first day begins in campus time. */
    public Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    /** The moment just after the last day ends in campus time. Orders before this moment are in range. */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
