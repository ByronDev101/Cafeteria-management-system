package ke.ac.kca.cafeteria.identity;

import java.util.List;

/** A rule was broken in a way the person can fix. Messages are safe to show on screen. */
public class UserAdminException extends RuntimeException {

    private final List<String> messages;

    public UserAdminException(String message) {
        super(message);
        this.messages = List.of(message);
    }

    public UserAdminException(List<String> messages) {
        super(String.join(" ", messages));
        this.messages = List.copyOf(messages);
    }

    public List<String> getMessages() {
        return messages;
    }
}
