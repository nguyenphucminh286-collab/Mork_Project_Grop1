package vn.edu.fpt.savora.enums;

/**
 * Trạng thái vận hành thực tế của bàn ăn trong nhà hàng Savora:
 * - AVAILABLE (Xanh lá): Bàn trống, sẵn sàng đón khách
 * - RESERVED (Vàng): Bàn đã được đặt trước trong khung giờ sắp tới
 * - OCCUPIED (Đỏ): Bàn đang có khách dùng bữa (có đơn DINE_IN đang hoạt động)
 * - CLEANING (Xanh dương): Khách đã kết thúc bữa ăn (Order CLOSED), nhân viên đang dọn bàn
 * - MAINTENANCE (Xám): Bàn đang bảo trì, tạm ngưng phục vụ
 */
public enum TableStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED,
    CLEANING,
    MAINTENANCE
}
