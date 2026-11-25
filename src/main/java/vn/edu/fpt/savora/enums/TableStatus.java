package vn.edu.fpt.savora.enums;

/**
 * Operational statuses for restaurant dining tables:
 * - AVAILABLE (Green): Empty table ready for walk-ins or reservations
 * - RESERVED (Yellow): Reserved for an upcoming dining slot
 * - OCCUPIED (Red): Guests currently seated with an active DINE_IN order
 * - CLEANING (Blue): Guests departed (order CLOSED) and staff are sanitizing the table
 * - MAINTENANCE (Gray): Temporarily out of service for maintenance
 */
public enum TableStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED,
    CLEANING,
    MAINTENANCE
}
