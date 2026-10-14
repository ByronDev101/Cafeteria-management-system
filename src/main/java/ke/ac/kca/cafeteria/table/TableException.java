package ke.ac.kca.cafeteria.table;

import java.util.List;

/** A table rule was broken in a way the person can fix. Messages are safe to show on screen. */
public class TableException extends RuntimeException {

    private final List<String> messages;

    public TableException(String message) {
        super(message);
        this.messages = List.of(message);
    }

    public TableException(List<String> messages) {
        super(String.join(" ", messages));
        this.messages = List.copyOf(messages);
    }

    public List<String> getMessages() {
        return messages;
    }
}
