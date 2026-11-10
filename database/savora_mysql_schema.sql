-- =====================================================================
-- SAVORA - HỆ THỐNG QUẢN LÝ NHÀ HÀNG (MySQL 8.0+)
-- Schema chuẩn (Non-destructive: Bảo toàn dữ liệu hiện có)
-- Đã nâng cấp:
--   1. Quản lý trạng thái bàn thực tế (AVAILABLE, RESERVED, OCCUPIED, CLEANING, MAINTENANCE)
--   2. Quản lý khung giờ đặt bàn (duration_minutes = 120, end_time, pending_expires_at)
--   3. Đồng bộ trạng thái Order <-> Bàn (thêm trạng thái CLOSED, closed_at, ràng buộc TAKEAWAY)
--   4. Bổ sung đầy đủ Composite Indexes cho truy vấn kiểm tra trùng lịch & khóa dòng (Row Locking)
-- =====================================================================

DROP DATABASE IF EXISTS savora_db;
CREATE DATABASE savora_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE savora_db;

-- 1. Bảng ROLES (Phân quyền: ADMIN, MANAGER, STAFF, CHEF, CUSTOMER)
CREATE TABLE IF NOT EXISTS roles (
    role_id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
) ENGINE=InnoDB;

-- 2. Bảng USERS (Tài khoản người dùng)
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500) DEFAULT 'https://res.cloudinary.com/dej5mxdrt/image/upload/v1780061324/OIP_dbbjuo.jpg',
    gender ENUM('MALE', 'FEMALE', 'OTHER') DEFAULT 'OTHER',
    date_of_birth DATE,
    status ENUM('PENDING', 'ACTIVE', 'LOCKED') NOT NULL DEFAULT 'PENDING',
    address VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 3. Bảng USER_ROLES (Bảng trung gian User - Role)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4. Bảng EMAIL_VERIFICATIONS (Lưu mã OTP xác thực email/quên mật khẩu)
CREATE TABLE IF NOT EXISTS email_verifications (
    verification_id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    expired_at DATETIME NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_otp (email, otp_code)
) ENGINE=InnoDB;

-- 5. Bảng CATEGORIES (Danh mục món ăn)
CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    image_url VARCHAR(500),
    display_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 6. Bảng DISHES (Món ăn trong thực đơn nhà hàng)
CREATE TABLE IF NOT EXISTS dishes (
    dish_id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL,
    dish_name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(12, 2) NOT NULL,
    discount_price DECIMAL(12, 2),
    thumbnail_url VARCHAR(500),
    is_signature BOOLEAN NOT NULL DEFAULT FALSE,
    preparation_time_minutes INT DEFAULT 15,
    status ENUM('AVAILABLE', 'OUT_OF_STOCK', 'HIDDEN') NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dishes_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
    INDEX idx_dishes_category_status (category_id, status)
) ENGINE=InnoDB;

-- 7. Bảng DISH_IMAGES (Thư viện ảnh chi tiết của từng món ăn)
CREATE TABLE IF NOT EXISTS dish_images (
    image_id INT AUTO_INCREMENT PRIMARY KEY,
    dish_id INT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_dish_images_dish FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 8. Bảng DINING_TABLES (Bàn ăn trong nhà hàng - Hỗ trợ CLEANING & MAINTENANCE)
CREATE TABLE IF NOT EXISTS dining_tables (
    table_id INT AUTO_INCREMENT PRIMARY KEY,
    table_code VARCHAR(30) NOT NULL UNIQUE, -- VD: T1-B01, T2-B05, VIP-01
    table_name VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,             -- VD: Tầng 1, Tầng 2, Sân vườn, Khu VIP
    capacity INT NOT NULL DEFAULT 4,        -- Số ghế ngồi
    status ENUM('AVAILABLE', 'RESERVED', 'OCCUPIED', 'CLEANING', 'MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE',
    note VARCHAR(255),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_table_capacity CHECK (capacity > 0),
    INDEX idx_tables_status_capacity (status, capacity),
    INDEX idx_tables_area (area)
) ENGINE=InnoDB;

-- 9. Bảng RESERVATIONS (Đặt bàn trước - Quản lý khung giờ & chống trùng lịch)
CREATE TABLE IF NOT EXISTS reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_code VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT,                        -- Có thể null nếu khách vãng lai đặt qua điện thoại
    customer_name VARCHAR(150) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    customer_email VARCHAR(150),
    table_id INT,                           -- Bàn được chọn / xếp
    reservation_date DATE NOT NULL,
    reservation_time TIME NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 120,
    end_time TIME NOT NULL,                 -- Thời điểm kết thúc dự kiến (reservation_time + duration_minutes)
    guest_count INT NOT NULL DEFAULT 2,
    special_request VARCHAR(500),
    status ENUM('PENDING', 'CONFIRMED', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') NOT NULL DEFAULT 'PENDING',
    pending_expires_at DATETIME NULL,       -- Thời hạn giữ chỗ tạm thời cho trạng thái PENDING
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_customer FOREIGN KEY (customer_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_reservations_table FOREIGN KEY (table_id) REFERENCES dining_tables(table_id) ON DELETE SET NULL,
    CONSTRAINT chk_reservation_guests CHECK (guest_count > 0),
    CONSTRAINT chk_reservation_duration CHECK (duration_minutes > 0),
    INDEX idx_reservations_conflict (table_id, reservation_date, status, reservation_time, end_time),
    INDEX idx_reservations_date_time (reservation_date, reservation_time),
    INDEX idx_reservations_customer (customer_id, reservation_date),
    INDEX idx_reservations_pending_exp (status, pending_expires_at)
) ENGINE=InnoDB;

-- Trigger tự động tính end_time = reservation_time + duration_minutes khi INSERT/UPDATE
DROP TRIGGER IF EXISTS trg_reservations_before_insert;
DELIMITER $$
CREATE TRIGGER trg_reservations_before_insert
BEFORE INSERT ON reservations
FOR EACH ROW
BEGIN
    IF NEW.duration_minutes IS NULL OR NEW.duration_minutes <= 0 THEN
        SET NEW.duration_minutes = 120;
    END IF;
    SET NEW.end_time = ADDTIME(NEW.reservation_time, SEC_TO_TIME(NEW.duration_minutes * 60));
    IF NEW.status = 'PENDING' AND NEW.pending_expires_at IS NULL THEN
        SET NEW.pending_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE);
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_reservations_before_update$$
CREATE TRIGGER trg_reservations_before_update
BEFORE UPDATE ON reservations
FOR EACH ROW
BEGIN
    IF NEW.duration_minutes IS NULL OR NEW.duration_minutes <= 0 THEN
        SET NEW.duration_minutes = 120;
    END IF;
    SET NEW.end_time = ADDTIME(NEW.reservation_time, SEC_TO_TIME(NEW.duration_minutes * 60));
END$$
DELIMITER ;

-- 10. Bảng VOUCHERS (Mã giảm giá nhà hàng)
CREATE TABLE IF NOT EXISTS vouchers (
    voucher_id INT AUTO_INCREMENT PRIMARY KEY,
    voucher_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    discount_percent INT,
    max_discount_amount DECIMAL(12, 2),
    min_order_value DECIMAL(12, 2) DEFAULT 0,
    quantity INT NOT NULL DEFAULT 100,
    start_date DATETIME NOT NULL,
    end_date DATETIME NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'DISABLED') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

-- 11. Bảng RESTAURANT_ORDERS (Đơn gọi món tại bàn / Mang về / Đặt trước - Đồng bộ với Bàn ăn)
CREATE TABLE IF NOT EXISTS restaurant_orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT,
    staff_id INT,                           -- Nhân viên phục vụ tạo/phụ trách đơn
    table_id INT,                           -- Bàn ăn (Bắt buộc NULL nếu order_type = 'TAKEAWAY')
    reservation_id INT,                     -- Liên kết phiếu đặt bàn (nếu có)
    voucher_id INT,
    order_type ENUM('DINE_IN', 'TAKEAWAY', 'PRE_ORDER') NOT NULL DEFAULT 'DINE_IN',
    subtotal_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status ENUM('OPEN', 'PREPARING', 'SERVED', 'PAID', 'CLOSED', 'CANCELLED') NOT NULL DEFAULT 'OPEN',
    note VARCHAR(500),
    closed_at DATETIME NULL,                -- Thời điểm đóng đơn (chuyển bàn sang CLEANING)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_staff FOREIGN KEY (staff_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES dining_tables(table_id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(voucher_id) ON DELETE SET NULL,
    INDEX idx_orders_table_status (table_id, status, order_type),
    INDEX idx_orders_customer_created (customer_id, created_at),
    INDEX idx_orders_reservation (reservation_id)
) ENGINE=InnoDB;

-- Trigger đảm bảo đơn TAKEAWAY không bao giờ chiếm bàn ăn (thay cho CHECK constraint do table_id là Foreign Key)
DROP TRIGGER IF EXISTS trg_orders_before_insert;
DELIMITER $$
CREATE TRIGGER trg_orders_before_insert
BEFORE INSERT ON restaurant_orders
FOR EACH ROW
BEGIN
    IF NEW.order_type = 'TAKEAWAY' THEN
        SET NEW.table_id = NULL;
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_orders_before_update$$
CREATE TRIGGER trg_orders_before_update
BEFORE UPDATE ON restaurant_orders
FOR EACH ROW
BEGIN
    IF NEW.order_type = 'TAKEAWAY' THEN
        SET NEW.table_id = NULL;
    END IF;
END$$
DELIMITER ;

-- 12. Bảng ORDER_ITEMS (Chi tiết từng món trong đơn gọi món)
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    dish_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(12, 2) NOT NULL,
    note VARCHAR(255),                      -- VD: Ít cay, không hành
    item_status ENUM('PENDING', 'COOKING', 'READY', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES restaurant_orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes(dish_id),
    INDEX idx_order_items_order_status (order_id, item_status)
) ENGINE=InnoDB;

-- 13. Bảng PAYMENTS (Thanh toán hóa đơn)
CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL UNIQUE,
    payment_method ENUM('CASH', 'BANK_TRANSFER', 'VN_PAY', 'CARD') NOT NULL DEFAULT 'CASH',
    payment_status ENUM('UNPAID', 'PAID', 'REFUNDED') NOT NULL DEFAULT 'UNPAID',
    paid_amount DECIMAL(12, 2) NOT NULL,
    transaction_ref VARCHAR(100),
    paid_at DATETIME,
    cashier_id INT,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES restaurant_orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_cashier FOREIGN KEY (cashier_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 14. Bảng REVIEWS (Đánh giá món ăn & chất lượng phục vụ)
CREATE TABLE IF NOT EXISTS reviews (
    review_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    dish_id INT,
    order_id INT,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_dish FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE SET NULL,
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES restaurant_orders(order_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =====================================================================
-- DỮ LIỆU MẪU KHỞI TẠO (SEED DATA - Dùng INSERT IGNORE để không ghi đè dữ liệu cũ)
-- =====================================================================

INSERT IGNORE INTO roles (role_id, role_name, description) VALUES
(1, 'ADMIN', 'Quản trị viên hệ thống / Chủ nhà hàng Savora'),
(2, 'CUSTOMER', 'Khách hàng đặt bàn và gọi món'),
(3, 'STAFF', 'Nhân viên phục vụ / Thu ngân / Quản lý bàn'),
(4, 'CHEF', 'Nhân viên bếp điều phối món ăn');

-- Mật khẩu mẫu cho cả 4 tài khoản: 123456 (Chuỗi BCrypt chuẩn của "123456")
INSERT IGNORE INTO users (user_id, first_name, last_name, email, phone, password_hash, gender, status) VALUES
(1, 'Quản Trị', 'Savora', 'admin@savora.vn', '0901000001', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'MALE', 'ACTIVE'),
(2, 'Nguyễn Văn', 'Khách', 'customer@savora.vn', '0901000002', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'MALE', 'ACTIVE'),
(3, 'Trần Thị', 'Phục Vụ', 'staff@savora.vn', '0901000003', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'FEMALE', 'ACTIVE'),
(4, 'Lê Hoàng', 'Bếp Trưởng', 'chef@savora.vn', '0901000004', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'MALE', 'ACTIVE');

INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);

INSERT IGNORE INTO categories (category_id, category_name, description, display_order) VALUES
(1, 'Khai vị & Salad', 'Các món súp, gỏi và salad tươi mát mở đầu bữa tiệc', 1),
(2, 'Món Chính Thượng Hạng', 'Bò Wagyu, Cá hồi Na Uy, Hải sản tươi sống chế biến chuẩn Âu - Á', 2),
(3, 'Lẩu & Nướng Savora', 'Các set lẩu và nướng đặc trưng tại bàn', 3),
(4, 'Tráng Miệng', 'Bánh ngọt Pháp, kem Gelato và trái cây theo mùa', 4),
(5, 'Đồ Uống & Rượu Vang', 'Cocktail, Mocktail, nước ép nguyên chất và rượu vang', 5);

INSERT IGNORE INTO dishes (dish_id, category_id, dish_name, description, price, discount_price, thumbnail_url, is_signature, preparation_time_minutes, status) VALUES
(1, 1, 'Salad Cá Hồi Áp Chảo Sốt Chanh Dây', 'Cá hồi Na Uy áp chảo dùng kèm rau hữu cơ Đà Lạt và sốt chanh dây chua dịu.', 165000, 149000, 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600', TRUE, 10, 'AVAILABLE'),
(2, 1, 'Súp Bí Đỏ Kem Tươi & Nấm Truffle', 'Súp bí đỏ Nhật Bản nấu cùng kem tươi Pháp và dầu nấm Truffle đen.', 95000, NULL, 'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=600', FALSE, 10, 'AVAILABLE'),
(3, 2, 'Thăn Nội Bò Úc Nướng Đá Nóng', 'Thăn nội bò Úc 250g nướng chín vừa kèm khoai tây nghiền và sốt tiêu đen.', 420000, 389000, 'https://images.unsplash.com/photo-1558030006-450675393462?w=600', TRUE, 20, 'AVAILABLE'),
(4, 2, 'Tôm Hùm Bỏ Lò Phô Mai Mozzarella', 'Tôm hùm bông tươi sống đút lò cùng phô mai Mozzarella béo ngậy.', 690000, 650000, 'https://images.unsplash.com/photo-1559737558-2f5a35f4523b?w=600', TRUE, 25, 'AVAILABLE'),
(5, 3, 'Lẩu Hải Sản Savora Đặc Biệt', 'Nước dùng chua cay đậm đà cùng tôm sú, mực ống, bạch tuộc, nghêu và nấm tươi.', 450000, NULL, 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=600', TRUE, 15, 'AVAILABLE'),
(6, 4, 'Bánh Lava Socola Tan Chảy & Kem Vani', 'Bánh socola Bỉ nướng nóng chảy nhân dùng kèm 1 viên kem Vani.', 89000, NULL, 'https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=600', FALSE, 12, 'AVAILABLE'),
(7, 5, 'Trà Đào Cam Sả Savora', 'Trà đen ủ lạnh kết hợp đào ngâm giòn, cam vàng và hương sả tươi.', 65000, NULL, 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=600', FALSE, 5, 'AVAILABLE');

INSERT IGNORE INTO dining_tables (table_code, table_name, area, capacity, status, note) VALUES
('T1-B01', 'Bàn 01 - Tầng 1', 'Tầng 1', 2, 'AVAILABLE', 'Bàn đôi cạnh cửa sổ'),
('T1-B02', 'Bàn 02 - Tầng 1', 'Tầng 1', 4, 'AVAILABLE', 'Bàn gia đình 4 người'),
('T1-B03', 'Bàn 03 - Tầng 1', 'Tầng 1', 4, 'OCCUPIED', 'Đang phục vụ khách tại bàn'),
('T1-B04', 'Bàn 04 - Tầng 1', 'Tầng 1', 4, 'CLEANING', 'Khách vừa rời bàn, đang dọn dẹp'),
('T2-B01', 'Bàn 01 - Tầng 2', 'Tầng 2', 6, 'RESERVED', 'Đã có khách đặt trước'),
('T2-B02', 'Bàn 02 - Tầng 2', 'Tầng 2', 8, 'AVAILABLE', 'Bàn nhóm 8 người'),
('VIP-01', 'Phòng VIP Hoa Sen', 'Khu VIP', 12, 'AVAILABLE', 'Phòng riêng có máy chiếu'),
('VIP-02', 'Phòng VIP Hoàng Gia', 'Khu VIP', 16, 'MAINTENANCE', 'Đang bảo trì điều hòa');

INSERT IGNORE INTO vouchers (voucher_code, description, discount_percent, max_discount_amount, min_order_value, quantity, start_date, end_date, status) VALUES
('SAVORA10', 'Giảm 10% tối đa 100.000đ cho hóa đơn từ 500.000đ', 10, 100000, 500000, 100, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE'),
('WELCOME50', 'Giảm 15% tối đa 150.000đ cho khách hàng đặt bàn lần đầu', 15, 150000, 300000, 50, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE');
