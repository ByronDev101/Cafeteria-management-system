package ke.ac.kca.cafeteria.identity;

/** Result of creating an account or resetting a password. The plain password is shown once, never stored or logged. */
public record CreatedAccount(String publicId, String username, String temporaryPassword) {

    @Override
    public String toString() {
        return "CreatedAccount[" + username + "]";
    }
}
