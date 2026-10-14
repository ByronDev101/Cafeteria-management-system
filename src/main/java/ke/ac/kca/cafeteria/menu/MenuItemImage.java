package ke.ac.kca.cafeteria.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The stored photo for one menu item (one row per item). Only ever holds a processed JPEG. */
@Entity
@Table(name = "menu_item_images")
public class MenuItemImage {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false, columnDefinition = "mediumblob")
    private byte[] data;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    protected MenuItemImage() {
    }

    public MenuItemImage(Long itemId, ImageProcessor.Processed processed) {
        this.itemId = itemId;
        replace(processed);
    }

    public final void replace(ImageProcessor.Processed processed) {
        this.contentType = "image/jpeg";
        this.sizeBytes = processed.jpeg().length;
        this.width = processed.width();
        this.height = processed.height();
        this.data = processed.jpeg();
        this.updatedAt = Instant.now();
    }

    public Long getItemId() {
        return itemId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}
