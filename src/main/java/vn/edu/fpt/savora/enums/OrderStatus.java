package vn.edu.fpt.savora.enums;

/**
 * Restaurant order lifecycle statuses:
 * - OPEN: Table opened / order created
 * - PREPARING: Kitchen is preparing dishes
 * - SERVED: All dishes served to the table
 * - PAID: Bill paid (guests may still remain seated before table is cleared)
 * - CLOSED: Guests departed and staff closed the order -> table moves to CLEANING
 * - CANCELLED: Order cancelled
 */
public enum OrderStatus {
    OPEN,
    PREPARING,
    SERVED,
    PAID,
    CLOSED,
    CANCELLED
}
