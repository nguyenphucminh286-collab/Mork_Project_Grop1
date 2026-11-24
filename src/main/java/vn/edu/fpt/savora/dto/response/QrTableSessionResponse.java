package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrTableSessionResponse {

    // 1. Dining Table Details
    private Integer tableId;
    private String tableCode;
    private String tableName;
    private String area;
    private Integer capacity;
    private String tableStatus;
    private String tableNote;

    // 2. Linked Today's Reservation (if booked online)
    private Integer reservationId;
    private String reservationCode;
    private String reservedCustomerName;
    private String reservedTimeSlot;
    private Integer reservedGuestCount;
    private String reservedVoucherCode;
    private Integer reservedDiscountPercent;

    // 3. Active Dine-In Order & Running Bill
    private Integer orderId;
    private String orderCode;
    private String orderStatus;
    private String appliedVoucherCode;
    private Integer appliedDiscountPercent;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private List<QrOrderedItemDto> placedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QrOrderedItemDto {
        private Integer orderItemId;
        private Integer dishId;
        private String dishName;
        private String thumbnailUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String note;
        private String itemStatus;
        private String orderedAt;
    }
}
