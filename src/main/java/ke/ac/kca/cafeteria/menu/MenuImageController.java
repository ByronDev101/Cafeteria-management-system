package ke.ac.kca.cafeteria.menu;

import java.time.Duration;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.WebRequest;

/**
 * Serves menu photos to signed-in users. Photos are not sensitive, but there is no reason to expose them
 * to the open internet, so the normal "must be signed in" rule applies. The URL carries the photo's
 * version, so browsers can keep a copy until the photo changes.
 */
@Controller
public class MenuImageController {

    private final MenuService service;

    public MenuImageController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/media/menu/{publicId}")
    public ResponseEntity<byte[]> image(@PathVariable String publicId, WebRequest request) {
        Optional<ImagePayload> found = service.image(publicId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ImagePayload image = found.get();
        String etag = "\"" + image.version() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .eTag(etag)
                .body(image.data());
    }
}
