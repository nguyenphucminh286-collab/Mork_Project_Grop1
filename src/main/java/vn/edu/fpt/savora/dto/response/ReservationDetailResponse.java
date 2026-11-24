package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDetailResponse {
    private Integer reservationId;
    private String reservationCode;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String reservationDate;
    private String reservationTime;
    private String endTime;
    private Integer durationMinutes;
    private Integer adultCount;
    private Integer childCount;
    private Integer guestCount;
    private String preferredArea;
    private Integer tableId;
    private String tableCode;
    private String tableName;
    private String voucherCode;
    private Integer discountPercent;
    private String occasion;
    private String specialRequest;
    private String status;
    private String cancelReason;
    private String createdAt;
}
