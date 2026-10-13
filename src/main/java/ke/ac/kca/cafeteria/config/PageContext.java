package ke.ac.kca.cafeteria.config;

import ke.ac.kca.cafeteria.identity.AppUserPrincipal;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.security.core.Authentication;

/** Small helpers for the text shown in the page header. */
public final class PageContext {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private PageContext() {
    }

    /** The signed-in person's first name in capitals, or their username when no display name is known. */
    public static String welcomeName(Principal principal) {
        String display = principal.getName();
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof AppUserPrincipal user
                && user.getDisplayName() != null
                && !user.getDisplayName().isBlank()) {
            display = user.getDisplayName();
        }
        return display.trim().split("\\s+")[0].toUpperCase(Locale.ROOT);
    }

    /** Today's date in campus time, for example "FRI, OCT 9". */
    public static String todayLabel(String zoneId) {
        return todayLabel(zoneId, Clock.systemUTC());
    }

    static String todayLabel(String zoneId, Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.of(zoneId))).format(DAY).toUpperCase(Locale.ROOT);
    }
}
