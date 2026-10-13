package ke.ac.kca.cafeteria.menu;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Saves, removes and loads menu photos, and keeps the item's image version in step. */
@Component
public class MenuImageStore {

    private final MenuItemImageRepository images;

    public MenuImageStore(MenuItemImageRepository images) {
        this.images = images;
    }

    public void save(MenuItem item, ImageProcessor.Processed processed) {
        images.findById(item.getId()).ifPresentOrElse(
                existing -> existing.replace(processed),
                () -> images.save(new MenuItemImage(item.getId(), processed)));
        item.setImageVersion(System.currentTimeMillis());
    }

    public void remove(MenuItem item) {
        images.findById(item.getId()).ifPresent(images::delete);
        item.setImageVersion(null);
    }

    public Optional<ImagePayload> load(MenuItem item) {
        return images.findById(item.getId()).map(image -> new ImagePayload(
                image.getContentType(), image.getData(),
                item.getImageVersion() == null ? 0L : item.getImageVersion()));
    }
}
