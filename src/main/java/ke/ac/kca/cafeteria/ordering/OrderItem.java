package ke.ac.kca.cafeteria.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** One line of an order. The name and price are copies taken when the order was placed (FR-015). */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "menu_item_id", updatable = false)
    private Long menuItemId;

    @Column(name = "item_name", nullable = false, updatable = false, length = 100)
    private String itemName;

    @Column(name = "unit_price", nullable = false, updatable = false, columnDefinition = "decimal(12,2)")
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, updatable = false, columnDefinition = "decimal(12,2)")
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    OrderItem(CustomerOrder order, Long menuItemId, String itemName, BigDecimal unitPrice, int quantity) {
        this.order = order;
        this.menuItemId = menuItemId;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }
}
