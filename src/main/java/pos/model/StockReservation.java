package pos.model;

import java.sql.Timestamp;

/**
 * Model class representing a stock reservation in the POS system.
 * Stock reservations temporarily hold inventory for items in the cart,
 * preventing overselling and ensuring stock availability during checkout.
 */
public class StockReservation {
    private Long reservationId;
    private String transactionId;  // Links all reservations for a single cart/transaction
    private Integer onlineInventoryItemId;
    private Integer inStoreInventoryId;
    private int quantity;
    private String channel; // "in-store", "online", or "both"
    private Timestamp reservedAt;
    private Timestamp expiresAt;

    // Default constructor
    public StockReservation() {
    }

    // Full constructor
    public StockReservation(Long reservationId, String transactionId,
                          Integer onlineInventoryItemId, Integer inStoreInventoryId, 
                          int quantity, String channel, Timestamp reservedAt, Timestamp expiresAt) {
        this.reservationId = reservationId;
        this.transactionId = transactionId;
        this.onlineInventoryItemId = onlineInventoryItemId;
        this.inStoreInventoryId = inStoreInventoryId;
        this.quantity = quantity;
        this.channel = channel;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
    }

    // Builder constructor for creating new reservations
    public StockReservation(String transactionId, Integer onlineInventoryItemId, 
                          Integer inStoreInventoryId, int quantity, String channel) {
        this.transactionId = transactionId;
        this.onlineInventoryItemId = onlineInventoryItemId;
        this.inStoreInventoryId = inStoreInventoryId;
        this.quantity = quantity;
        this.channel = channel;
        this.reservedAt = new Timestamp(System.currentTimeMillis());
        // Set expiration to 15 minutes from now
        this.expiresAt = new Timestamp(System.currentTimeMillis() + (15 * 60 * 1000));
    }

    // Getters and Setters
    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
    
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Integer getOnlineInventoryItemId() {
        return onlineInventoryItemId;
    }

    public void setOnlineInventoryItemId(Integer onlineInventoryItemId) {
        this.onlineInventoryItemId = onlineInventoryItemId;
    }

    public Integer getInStoreInventoryId() {
        return inStoreInventoryId;
    }

    public void setInStoreInventoryId(Integer inStoreInventoryId) {
        this.inStoreInventoryId = inStoreInventoryId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Timestamp getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(Timestamp reservedAt) {
        this.reservedAt = reservedAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Check if this reservation has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Check if this reservation is active (not expired)
     */
    public boolean isActive() {
        return !isExpired();
    }

    @Override
    public String toString() {
        return "StockReservation{" +
                "reservationId=" + reservationId +
                ", transactionId='" + transactionId + '\'' +
                ", onlineInventoryItemId=" + onlineInventoryItemId +
                ", inStoreInventoryId=" + inStoreInventoryId +
                ", quantity=" + quantity +
                ", channel='" + channel + '\'' +
                ", reservedAt=" + reservedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
