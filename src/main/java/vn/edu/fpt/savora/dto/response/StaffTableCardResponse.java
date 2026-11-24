package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffTableCardResponse {
    private Integer tableId;
    private String tableCode;
    private String tableName;
    private String area;
    private Integer capacity;
    private String status;
    private String note;

    // Today's reservation summary (if any)
    private Integer reservationId;
    private String reservationCode;
    private String reservedCustomerName;
    private String reservedCustomerPhone;
    private String reservedTimeSlot;
    private Integer reservedGuestCount;
    private String reservationStatus;

    // Active order summary (if any)
    private Integer orderId;
    private String orderCode;
    private BigDecimal totalAmount;
    private int totalItemsCount;
    private int pendingItemsCount;
    private int cookingItemsCount;
    private int readyItemsCount;
    private int servedItemsCount;
}
