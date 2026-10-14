package ke.ac.kca.cafeteria.table;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/** Reads and writes the selected table in the student's session. Signing out ends the session and clears it. */
public final class TableSession {

    static final String ATTRIBUTE = "selectedTable";

    private TableSession() {
    }

    public static void store(HttpSession session, SelectedTable table) {
        session.setAttribute(ATTRIBUTE, table);
    }

    public static Optional<SelectedTable> read(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(ATTRIBUTE);
        return value instanceof SelectedTable table ? Optional.of(table) : Optional.empty();
    }

    public static void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }
}
