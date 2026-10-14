package ke.ac.kca.cafeteria.table;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds the address stored in a table's QR code. Set app.public-base-url (or the PUBLIC_BASE_URL environment
 * variable) to the address phones can reach, for example http://192.168.1.20:8080. When it is empty, the address
 * the administrator is using right now is used.
 */
@Component
public class PublicUrl {

    private final String configured;

    public PublicUrl(@Value("${app.public-base-url:}") String configured) {
        this.configured = configured == null ? "" : configured.trim();
    }

    public String tableUrl(HttpServletRequest request, String token) {
        String base = configured.isEmpty()
                ? ServletUriComponentsBuilder.fromContextPath(request).build().toUriString()
                : configured;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/t/" + token;
    }
}
