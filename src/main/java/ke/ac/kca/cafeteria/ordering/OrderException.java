package ke.ac.kca.cafeteria.ordering;

import java.util.List;

/** An ordering rule was broken in a way the person can fix. Messages are safe to show on screen. */
public class OrderException extends RuntimeException {

    private final List<String> messages;

    public OrderException(String message) {
        super(message);
        this.messages = List.of(message);
    }

    public OrderException(List<String> messages) {
        super(String.join(" ", messages));
        this.messages = List.copyOf(messages);
    }

    public List<String> getMessages() {
        return messages;
    }
}
