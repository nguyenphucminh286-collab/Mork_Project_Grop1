package vn.edu.fpt.savora.enums;

/**
 * Trạng thái đơn hàng nhà hàng Savora:
 * - OPEN: Mới mở bàn / vừa tạo đơn
 * - PREPARING: Bếp đang chế biến
 * - SERVED: Đã lên đủ món
 * - PAID: Đã thanh toán hóa đơn (nhưng khách có thể vẫn đang ngồi tại bàn -> chưa giải phóng bàn)
 * - CLOSED: Khách đã rời bàn, nhân viên đóng đơn -> chuyển trạng thái bàn sang CLEANING
 * - CANCELLED: Đơn bị hủy
 */
public enum OrderStatus {
    OPEN,
    PREPARING,
    SERVED,
    PAID,
    CLOSED,
    CANCELLED
}
