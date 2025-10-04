# Stock Reservation System - Quick Reference Guide

## How It Works

### The Problem
Without reservations, two customers could add the last item to their carts simultaneously, but only one can successfully checkout. This leads to overselling and poor customer experience.

### The Solution
When a customer adds an item to their cart, the system immediately **reserves** that stock for 15 minutes. This prevents other customers from claiming the same stock.

## Key Concepts

### Available Stock Calculation
```
Available Stock = Total Physical Stock - Reserved Quantity
```

**Example:**
- Physical Stock: 10 units
- Reserved by other carts: 3 units
- **Available for new carts: 7 units**

### Reservation Lifecycle

1. **Created** - When item added to cart (15-minute timer starts)
2. **Updated** - When cart quantity changes
3. **Released** - When item removed from cart OR checkout completes
4. **Expired** - After 15 minutes if not checked out (automatically cleaned up)

## API Reference

### Check Stock Availability
```java
StockAvailability availability = 
    StockReservationDAO.checkAvailableStock(conn, sku, requestedQty);

if (availability.isAvailable) {
    // Stock is available
    System.out.println("Total: " + availability.totalStock);
    System.out.println("Reserved: " + availability.reservedQuantity);
    System.out.println("Available: " + availability.availableStock);
} else {
    // Not enough stock
    System.out.println(availability.message);
}
```

### Create Reservation
```java
Long reservationId = StockReservationDAO.createReservation(conn, sku, quantity);
// Returns reservation ID or throws SQLException if stock unavailable
```

### Update Reservation Quantity
```java
boolean success = StockReservationDAO.updateReservationQuantity(
    conn, sku, oldQuantity, newQuantity);
// Returns true if successful
// Throws SQLException if insufficient stock for increase
```

### Release Reservation
```java
int released = StockReservationDAO.releaseReservation(conn, sku, quantity);
// Returns number of reservations released (usually 1)
```

### Confirm Reservations (at Checkout)
```java
List<CartItemData> cartItems = new ArrayList<>();
for (CartItem item : cart) {
    cartItems.add(new CartItemData(item.getProduct().getSku(), item.getQuantity()));
}

int confirmed = StockReservationDAO.confirmReservations(conn, cartItems);
// Releases all reservations for the cart
```

### Cleanup Expired Reservations
```java
int cleaned = StockReservationDAO.cleanupExpiredReservations(conn);
System.out.println("Cleaned up " + cleaned + " expired reservations");
```

## Database Queries

### Check Active Reservations for a Product
```sql
SELECT 
    reservation_id,
    quantity,
    reserved_at,
    expires_at,
    TIMESTAMPDIFF(MINUTE, NOW(), expires_at) AS minutes_remaining
FROM stock_reservation
WHERE (online_inventory_item_id = ? OR in_store_inventory_id = ?)
AND status = 'pending'
AND expires_at > NOW()
ORDER BY reserved_at;
```

### Total Reserved Quantity
```sql
SELECT COALESCE(SUM(quantity), 0) AS reserved_qty
FROM stock_reservation
WHERE (online_inventory_item_id = ? OR in_store_inventory_id = ?)
AND status = 'pending'
AND expires_at > NOW();
```

### Find Expired Reservations
```sql
SELECT *
FROM stock_reservation
WHERE status = 'pending'
AND expires_at <= NOW();
```

## Troubleshooting

### Stock Shows as Unavailable But Appears in Stock
**Possible Causes:**
1. Active reservations from other carts
2. Expired reservations not yet cleaned up

**Solutions:**
- Wait for periodic cleanup (runs every 5 minutes)
- Manually run cleanup: `StockReservationDAO.cleanupExpiredReservations(conn)`
- Check active reservations: `StockReservationDAO.getActiveReservations(conn, sku)`

### Reservations Not Released After Checkout
**Check:**
1. Was `confirmReservations()` called in `processPayment()`?
2. Check application logs for errors
3. Verify database transaction committed successfully

**Manual Cleanup:**
```sql
DELETE FROM stock_reservation
WHERE status = 'pending'
AND reserved_at < DATE_SUB(NOW(), INTERVAL 15 MINUTE);
```

### User Can't Add Item Even Though Stock Exists
**Debugging Steps:**
1. Check total stock vs reserved quantity
2. Verify reservation expiration times
3. Check for stale/orphaned reservations

```java
// Debug info
StockAvailability avail = StockReservationDAO.checkAvailableStock(conn, sku, 1);
System.out.println("Total: " + avail.totalStock);
System.out.println("Reserved: " + avail.reservedQuantity);
System.out.println("Available: " + avail.availableStock);
System.out.println("Message: " + avail.message);

// List active reservations
List<StockReservation> active = StockReservationDAO.getActiveReservations(conn, sku);
for (StockReservation r : active) {
    System.out.println(r);
}
```

## Monitoring

### Key Metrics to Monitor
1. **Average reservation duration** - How long items stay in cart
2. **Expiration rate** - % of reservations that expire vs checkout
3. **Peak concurrent reservations** - Maximum simultaneous carts
4. **Cleanup frequency** - How often cleanup finds expired items

### Sample Monitoring Queries

#### Active Reservations Count
```sql
SELECT COUNT(*) as active_reservations
FROM stock_reservation
WHERE status = 'pending'
AND expires_at > NOW();
```

#### Reservations by Status
```sql
SELECT 
    status,
    COUNT(*) as count,
    SUM(quantity) as total_quantity
FROM stock_reservation
GROUP BY status;
```

#### Top Reserved Products
```sql
SELECT 
    i.item_name,
    i.description,
    COUNT(DISTINCT sr.reservation_id) as reservation_count,
    SUM(sr.quantity) as total_reserved
FROM stock_reservation sr
LEFT JOIN in_store_product_details ispd ON sr.in_store_inventory_id = ispd.id
LEFT JOIN online_product_variant opv ON sr.online_inventory_item_id = opv.id
LEFT JOIN inventory i ON (ispd.inventory_product_id = i.id OR opv.online_product_id IN 
    (SELECT id FROM online_product_details WHERE product_id = i.id))
WHERE sr.status = 'pending'
AND sr.expires_at > NOW()
GROUP BY i.id
ORDER BY total_reserved DESC
LIMIT 10;
```

## Configuration

### Adjusting Reservation Timeout
Currently set to **15 minutes** in `StockReservation.java`:

```java
// Constructor
this.expiresAt = new Timestamp(System.currentTimeMillis() + (15 * 60 * 1000));
                                                            // ^^ Change here
```

### Adjusting Cleanup Frequency
Currently runs every **5 minutes** in `POSView.java`:

```java
Timeline cleanupTimeline = new Timeline(
    new KeyFrame(Duration.minutes(5), event -> { // <-- Change here
        // Cleanup logic
    })
);
```

## Best Practices

### DO:
✅ Always check availability before creating reservations  
✅ Release reservations when items removed from cart  
✅ Confirm reservations after successful checkout  
✅ Handle SQLException gracefully with user-friendly messages  
✅ Log all reservation operations for debugging  

### DON'T:
❌ Create reservations without checking availability  
❌ Forget to release reservations on cart abandonment  
❌ Block UI thread with reservation operations  
❌ Ignore cleanup failures silently  
❌ Hardcode timeout values in multiple places  

## Integration Checklist

When adding stock reservation to a new cart operation:

- [ ] Check stock availability first
- [ ] Create/update reservation if available
- [ ] Handle SQLException with user message
- [ ] Release reservation on failure
- [ ] Update UI to reflect availability
- [ ] Log the operation
- [ ] Test concurrent access scenario

## Support

For issues or questions:
1. Check application logs for errors
2. Verify database schema matches expected structure
3. Run manual cleanup to clear stale data
4. Review active reservations for the affected product
5. Check system clock synchronization (for expiration times)
