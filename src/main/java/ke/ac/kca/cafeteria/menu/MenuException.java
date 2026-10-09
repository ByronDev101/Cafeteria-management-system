package ke.ac.kca.cafeteria.menu;

import java.util.List;

/** A menu rule was broken in a way the person can fix. Messages are safe to show on screen. */
public class MenuException extends RuntimeException {

    private final List<String> messages;

    public MenuException(String message) {
        super(message);
        this.messages = List.of(message);
    }

    public MenuException(List<String> messages) {
        super(String.join(" ", messages));
        this.messages = List.copyOf(messages);
    }

    public List<String> getMessages() {
        return messages;
    }
}
