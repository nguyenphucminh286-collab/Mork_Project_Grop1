-- =====================================================================
-- SAVORA - RESTAURANT MANAGEMENT SYSTEM (MySQL 8.0+)
-- UNIFIED ONE-CLICK DATABASE INITIALIZATION & SEED SCRIPT
-- Run this single file in MySQL Workbench to create all tables,
-- triggers, indexes, and sample seed data for the entire team.
-- =====================================================================

DROP DATABASE IF EXISTS savora_db;
CREATE DATABASE savora_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE savora_db;

-- ---------------------------------------------------------------------
-- 1. Table: ROLES (Role-based access control: ADMIN, CUSTOMER, STAFF, CHEF)
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    role_id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. Table: USERS (System accounts & OAuth2 social accounts)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500) DEFAULT 'https://res.cloudinary.com/dej5mxdrt/image/upload/v1780061324/OIP_dbbjuo.jpg',
    gender ENUM('MALE', 'FEMALE', 'OTHER') DEFAULT 'OTHER',
    date_of_birth DATE,
    status ENUM('PENDING', 'ACTIVE', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
    address VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. Table: USER_ROLES (Many-to-Many join table between Users and Roles)
-- ---------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. Table: EMAIL_VERIFICATIONS (OTP codes for email verification / password reset)
-- ---------------------------------------------------------------------
CREATE TABLE email_verifications (
    verification_id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    expired_at DATETIME NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_otp (email, otp_code)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. Table: CATEGORIES (Menu categories)
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    image_url VARCHAR(500),
    display_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 6. Table: DISHES (Restaurant menu items)
-- ---------------------------------------------------------------------
CREATE TABLE dishes (
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

-- ---------------------------------------------------------------------
-- 7. Table: DISH_IMAGES (Gallery images for each dish)
-- ---------------------------------------------------------------------
CREATE TABLE dish_images (
    image_id INT AUTO_INCREMENT PRIMARY KEY,
    dish_id INT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_dish_images_dish FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 8. Table: DINING_TABLES (Real-time restaurant table statuses)
-- ---------------------------------------------------------------------
CREATE TABLE dining_tables (
    table_id INT AUTO_INCREMENT PRIMARY KEY,
    table_code VARCHAR(30) NOT NULL UNIQUE, -- e.g. T1-B01, T2-B01, VIP-01
    table_name VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,             -- e.g. 1st Floor, 2nd Floor, VIP Zone
    capacity INT NOT NULL DEFAULT 4,
    status ENUM('AVAILABLE', 'RESERVED', 'OCCUPIED', 'CLEANING', 'MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE',
    note VARCHAR(255),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_table_capacity CHECK (capacity > 0),
    INDEX idx_tables_status_capacity (status, capacity),
    INDEX idx_tables_area (area)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 9. Table: VOUCHERS (Promotional offers for PasGo-style booking & checkout)
-- ---------------------------------------------------------------------
CREATE TABLE vouchers (
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

-- ---------------------------------------------------------------------
-- 10. Table: RESERVATIONS (PasGo-style online table reservations & time slots)
-- ---------------------------------------------------------------------
CREATE TABLE reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_code VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT,                        -- Linked Customer account (nullable for phone walk-in bookings)
    customer_name VARCHAR(150) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    customer_email VARCHAR(150),
    table_id INT,                           -- Selected / assigned dining table
    voucher_id INT,                         -- Promotional voucher selected during PasGo booking
    preferred_area VARCHAR(100),            -- Preferred dining zone (1st Floor, 2nd Floor, VIP Zone)
    occasion VARCHAR(100),                  -- Dining occasion (Birthday, Dating, Family, Business, Anniversary)
    reservation_date DATE NOT NULL,
    reservation_time TIME NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 120,
    end_time TIME NOT NULL,                 -- Automatically calculated: reservation_time + duration_minutes
    adult_count INT NOT NULL DEFAULT 2,     -- Number of adults (PasGo form field)
    child_count INT NOT NULL DEFAULT 0,     -- Number of children (PasGo form field)
    guest_count INT NOT NULL DEFAULT 2,     -- Total guests = adult_count + child_count
    special_request VARCHAR(500),
    cancel_reason VARCHAR(255),
    status ENUM('PENDING', 'CONFIRMED', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') NOT NULL DEFAULT 'PENDING',
    pending_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_customer FOREIGN KEY (customer_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_reservations_table FOREIGN KEY (table_id) REFERENCES dining_tables(table_id) ON DELETE SET NULL,
    CONSTRAINT fk_reservations_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(voucher_id) ON DELETE SET NULL,
    CONSTRAINT chk_reservation_adults CHECK (adult_count > 0),
    CONSTRAINT chk_reservation_children CHECK (child_count >= 0),
    CONSTRAINT chk_reservation_guests CHECK (guest_count > 0),
    CONSTRAINT chk_reservation_duration CHECK (duration_minutes > 0),
    INDEX idx_reservations_conflict (table_id, reservation_date, status, reservation_time, end_time),
    INDEX idx_reservations_date_time (reservation_date, reservation_time),
    INDEX idx_reservations_customer (customer_id, reservation_date),
    INDEX idx_reservations_pending_exp (status, pending_expires_at)
) ENGINE=InnoDB;

-- Triggers for reservations: auto-compute guest_count, end_time, and pending_expires_at
DELIMITER $$
CREATE TRIGGER trg_reservations_before_insert
BEFORE INSERT ON reservations
FOR EACH ROW
BEGIN
    IF NEW.adult_count IS NULL OR NEW.adult_count <= 0 THEN
        SET NEW.adult_count = 2;
    END IF;
    IF NEW.child_count IS NULL OR NEW.child_count < 0 THEN
        SET NEW.child_count = 0;
    END IF;
    SET NEW.guest_count = NEW.adult_count + NEW.child_count;

    IF NEW.duration_minutes IS NULL OR NEW.duration_minutes <= 0 THEN
        SET NEW.duration_minutes = 120;
    END IF;
    SET NEW.end_time = ADDTIME(NEW.reservation_time, SEC_TO_TIME(NEW.duration_minutes * 60));

    IF NEW.status = 'PENDING' AND NEW.pending_expires_at IS NULL THEN
        SET NEW.pending_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE);
    END IF;
END$$

CREATE TRIGGER trg_reservations_before_update
BEFORE UPDATE ON reservations
FOR EACH ROW
BEGIN
    IF NEW.adult_count IS NULL OR NEW.adult_count <= 0 THEN
        SET NEW.adult_count = 2;
    END IF;
    IF NEW.child_count IS NULL OR NEW.child_count < 0 THEN
        SET NEW.child_count = 0;
    END IF;
    SET NEW.guest_count = NEW.adult_count + NEW.child_count;

    IF NEW.duration_minutes IS NULL OR NEW.duration_minutes <= 0 THEN
        SET NEW.duration_minutes = 120;
    END IF;
    SET NEW.end_time = ADDTIME(NEW.reservation_time, SEC_TO_TIME(NEW.duration_minutes * 60));
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 11. Table: RESTAURANT_ORDERS (Dine-in, Takeaway & Pre-order synchronized with tables)
-- ---------------------------------------------------------------------
CREATE TABLE restaurant_orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT,
    staff_id INT,
    table_id INT,                           -- Always NULL if order_type = 'TAKEAWAY'
    reservation_id INT,
    voucher_id INT,
    order_type ENUM('DINE_IN', 'TAKEAWAY', 'PRE_ORDER') NOT NULL DEFAULT 'DINE_IN',
    subtotal_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status ENUM('OPEN', 'PREPARING', 'SERVED', 'PAID', 'CLOSED', 'CANCELLED') NOT NULL DEFAULT 'OPEN',
    note VARCHAR(500),
    closed_at DATETIME NULL,
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

-- Triggers for restaurant_orders: ensure TAKEAWAY orders never occupy a dining table
DELIMITER $$
CREATE TRIGGER trg_orders_before_insert
BEFORE INSERT ON restaurant_orders
FOR EACH ROW
BEGIN
    IF NEW.order_type = 'TAKEAWAY' THEN
        SET NEW.table_id = NULL;
    END IF;
END$$

CREATE TRIGGER trg_orders_before_update
BEFORE UPDATE ON restaurant_orders
FOR EACH ROW
BEGIN
    IF NEW.order_type = 'TAKEAWAY' THEN
        SET NEW.table_id = NULL;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 12. Table: ORDER_ITEMS (Line items in each restaurant order)
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    dish_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(12, 2) NOT NULL,
    note VARCHAR(255),
    item_status ENUM('PENDING', 'COOKING', 'READY', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES restaurant_orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes(dish_id),
    INDEX idx_order_items_order_status (order_id, item_status)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 13. Table: PAYMENTS (Order billing & transaction records)
-- ---------------------------------------------------------------------
CREATE TABLE payments (
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

-- ---------------------------------------------------------------------
-- 14. Table: REVIEWS (Customer ratings & feedback)
-- ---------------------------------------------------------------------
CREATE TABLE reviews (
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
-- SAMPLE SEED DATA (READY FOR IMMEDIATE TESTING BY ALL TEAM MEMBERS)
-- =====================================================================

-- 1. Roles
INSERT INTO roles (role_id, role_name, description) VALUES
(1, 'ADMIN', 'System Administrator / Restaurant Owner'),
(2, 'CUSTOMER', 'Guest / Member booking tables and ordering dishes'),
(3, 'STAFF', 'Service Staff / Cashier / Floor Coordinator'),
(4, 'CHEF', 'Executive & Kitchen Chef managing dish preparation');

-- 2. Default Users (Default password for all 4 accounts: 123456)
INSERT INTO users (user_id, first_name, last_name, email, phone, password_hash, gender, status) VALUES
(1, 'Admin', 'Savora', 'admin@savora.vn', '0901000001', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'MALE', 'ACTIVE'),
(2, 'Minh', 'Nguyen', 'customer@savora.vn', '0901000002', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'MALE', 'ACTIVE'),
(3, 'Lan', 'Tran', 'staff@savora.vn', '0901000003', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'FEMALE', 'ACTIVE'),
(4, 'Hoang', 'Le', 'chef@savora.vn', '0901000004', '$2a$10$Uf98CaGQTkZ1qUU23ZEsKe4T..TaRod5UjllPZommV7d0RDig2pXO', 'MALE', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);

-- 3. Categories
INSERT INTO categories (category_id, category_name, description, display_order) VALUES
(1, 'Starters & Salads', 'Refreshing organic salads, artisan bisques, and light appetizers', 1),
(2, 'Prime Steaks & Seafood', 'Imported Australian Wagyu, Norwegian salmon, and live Nha Trang seafood', 2),
(3, 'Hotpot & Grill', 'Signature table-side hotpot and charcoal grill sets', 3),
(4, 'Artisan Desserts', 'French pastries, Italian gelato, and seasonal fruit platters', 4),
(5, 'Wines & Cocktails', 'Fine vintage wines, craft cocktails, mocktails, and cold-brewed teas', 5);

-- 4. Dishes
INSERT INTO dishes (dish_id, category_id, dish_name, description, price, discount_price, thumbnail_url, is_signature, preparation_time_minutes, status) VALUES
(1, 1, 'Pan-Seared Norwegian Salmon Salad', 'Pan-seared Norwegian salmon fillet served over organic Da Lat greens with passion fruit vinaigrette.', 165000, 149000, 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600', TRUE, 10, 'AVAILABLE'),
(2, 1, 'Roasted Kabocha Pumpkin & Black Truffle Soup', 'Velvety Japanese pumpkin bisque finished with French heavy cream and black truffle oil.', 95000, NULL, 'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=600', FALSE, 10, 'AVAILABLE'),
(3, 2, 'Lava-Stone Grilled Australian Tenderloin (250g)', 'Prime grass-fed Australian beef tenderloin grilled medium-rare, served with truffle mash and peppercorn jus.', 420000, 389000, 'https://images.unsplash.com/photo-1558030006-450675393462?w=600', TRUE, 20, 'AVAILABLE'),
(4, 2, 'Oven-Baked Spiny Lobster with Mozzarella', 'Whole fresh spiny lobster baked with aged Parmigiano-Reggiano, melted mozzarella, and garlic herb butter.', 690000, 650000, 'https://images.unsplash.com/photo-1559737558-2f5a35f4523b?w=600', TRUE, 25, 'AVAILABLE'),
(5, 3, 'Savora Royal Seafood Hotpot Set', 'Aromatic lemongrass broth accompanied by tiger prawns, squid, octopus, Manila clams, and wild mushrooms.', 450000, NULL, 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=600', TRUE, 15, 'AVAILABLE'),
(6, 4, 'Belgian Molten Chocolate Lava Cake & Gelato', 'Warm 70% dark Belgian chocolate fondant with a liquid ganache center, paired with Madagascar vanilla gelato.', 89000, NULL, 'https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=600', FALSE, 12, 'AVAILABLE'),
(7, 5, 'Savora Signature Peach, Orange & Lemongrass Brew', 'Cold-brewed artisanal black tea infused with crisp white peach slices, Valencia orange, and fresh lemongrass.', 65000, NULL, 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=600', FALSE, 5, 'AVAILABLE');

-- 5. Dining Tables (8 tables covering all 5 operational statuses)
INSERT INTO dining_tables (table_id, table_code, table_name, area, capacity, status, note) VALUES
(1, 'T1-B01', 'Table 01 — Window Side', '1st Floor', 2, 'AVAILABLE', 'Romantic window-side table for two'),
(2, 'T1-B02', 'Table 02 — Main Hall', '1st Floor', 4, 'AVAILABLE', 'Standard family booth (4 seats)'),
(3, 'T1-B03', 'Table 03 — Main Hall', '1st Floor', 4, 'OCCUPIED', 'Active dine-in order in progress'),
(4, 'T1-B04', 'Table 04 — Main Hall', '1st Floor', 4, 'CLEANING', 'Guests departed — staff sanitizing table'),
(5, 'T2-B01', 'Table 01 — Garden Terrace', '2nd Floor', 6, 'RESERVED', 'Reserved for evening dining slot'),
(6, 'T2-B02', 'Table 02 — Garden Terrace', '2nd Floor', 8, 'AVAILABLE', 'Spacious group dining table overlooking terrace'),
(7, 'VIP-01', 'VIP Lotus Private Suite', 'VIP Zone', 12, 'AVAILABLE', 'Private suite with projector & sound system'),
(8, 'VIP-02', 'VIP Royal Banquet Room', 'VIP Zone', 16, 'MAINTENANCE', 'Scheduled air-conditioning maintenance');

-- 6. Vouchers (PasGo-style booking & bill promotions)
INSERT INTO vouchers (voucher_id, voucher_code, description, discount_percent, max_discount_amount, min_order_value, quantity, start_date, end_date, status) VALUES
(1, 'SAVORA10', '10% OFF total dine-in bill (up to 100,000 VND for orders from 500,000 VND)', 10, 100000, 500000, 100, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE'),
(2, 'WELCOME50', '15% OFF first online table reservation (up to 150,000 VND for orders from 300,000 VND)', 15, 150000, 300000, 50, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE'),
(3, 'VIPPARTY20', '20% OFF VIP Private Suite booking for birthday & corporate parties (up to 300,000 VND)', 20, 300000, 1000000, 30, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE');

-- 7. Sample Reservations (Demonstrating PasGo fields: adult_count, child_count, voucher_id, occasion, preferred_area)
INSERT INTO reservations (
    reservation_code, customer_id, customer_name, customer_phone, customer_email,
    table_id, voucher_id, preferred_area, occasion,
    reservation_date, reservation_time, duration_minutes,
    adult_count, child_count, special_request, status
) VALUES
(
    'SAV-1001', 2, 'Minh Nguyen', '0901000002', 'customer@savora.vn',
    5, 2, '2nd Floor', 'Birthday',
    CURDATE(), '19:00:00', 120,
    4, 2, 'Need 1 baby high chair and a window-side view for birthday cake.', 'CONFIRMED'
),
(
    'SAV-1002', 2, 'Minh Nguyen', '0901000002', 'customer@savora.vn',
    2, 1, '1st Floor', 'Family Dining',
    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:30:00', 120,
    4, 0, 'Quiet corner table preferred.', 'PENDING'
);

-- 8. Sample Reviews
INSERT INTO reviews (customer_id, dish_id, rating, comment) VALUES
(2, 3, 5, 'The Australian Tenderloin was cooked to perfection and the online table booking with promotional voucher was super convenient!'),
(2, 4, 5, 'Fresh lobster and wonderful service in the Garden Terrace area.');
