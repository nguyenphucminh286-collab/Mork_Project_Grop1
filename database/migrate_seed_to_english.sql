-- =====================================================================
-- SAVORA - Migrate legacy Vietnamese seed records in savora_db to English
-- Safe & idempotent: preserves user-created accounts, feedback, orders,
-- and reservations while updating default seed data to English.
-- =====================================================================
USE savora_db;
SET NAMES utf8mb4;

-- 1. Roles
UPDATE roles SET description = 'System Administrator / Restaurant Owner' WHERE role_name = 'ADMIN';
UPDATE roles SET description = 'Guest / Member booking tables and ordering dishes' WHERE role_name = 'CUSTOMER';
UPDATE roles SET description = 'Service Staff / Cashier / Floor Coordinator' WHERE role_name = 'STAFF';
UPDATE roles SET description = 'Executive & Kitchen Chef managing dish preparation' WHERE role_name = 'CHEF';

-- 2. Default seeded accounts (preserves any custom user accounts)
UPDATE users SET first_name = 'Admin', last_name = 'Savora' WHERE LOWER(email) = 'admin@savora.vn';
UPDATE users SET first_name = 'Minh', last_name = 'Nguyen' WHERE LOWER(email) = 'customer@savora.vn';
UPDATE users SET first_name = 'Lan', last_name = 'Tran' WHERE LOWER(email) = 'staff@savora.vn';
UPDATE users SET first_name = 'Hoang', last_name = 'Le' WHERE LOWER(email) = 'chef@savora.vn';

-- 3. Menu categories
UPDATE categories
SET category_name = 'Starters & Salads',
    description = 'Refreshing organic salads, artisan bisques, and light appetizers'
WHERE category_id = 1;

UPDATE categories
SET category_name = 'Prime Steaks & Seafood',
    description = 'Imported Australian Wagyu, Norwegian salmon, and live Nha Trang seafood'
WHERE category_id = 2;

UPDATE categories
SET category_name = 'Hotpot & Grill',
    description = 'Signature table-side hotpot and charcoal grill sets'
WHERE category_id = 3;

UPDATE categories
SET category_name = 'Artisan Desserts',
    description = 'French pastries, Italian gelato, and seasonal fruit platters'
WHERE category_id = 4;

UPDATE categories
SET category_name = 'Wines & Cocktails',
    description = 'Fine vintage wines, craft cocktails, mocktails, and cold-brewed teas'
WHERE category_id = 5;

-- 4. Menu dishes
UPDATE dishes
SET dish_name = 'Pan-Seared Norwegian Salmon Salad',
    description = 'Pan-seared Norwegian salmon fillet served over organic Da Lat greens with passion fruit vinaigrette.'
WHERE dish_id = 1;

UPDATE dishes
SET dish_name = 'Roasted Kabocha Pumpkin & Black Truffle Soup',
    description = 'Velvety Japanese pumpkin bisque finished with French heavy cream and black truffle oil.'
WHERE dish_id = 2;

UPDATE dishes
SET dish_name = 'Lava-Stone Grilled Australian Tenderloin (250g)',
    description = 'Prime grass-fed Australian beef tenderloin grilled medium-rare, served with truffle mash and peppercorn jus.'
WHERE dish_id = 3;

UPDATE dishes
SET dish_name = 'Oven-Baked Spiny Lobster with Mozzarella',
    description = 'Whole fresh spiny lobster baked with aged Parmigiano-Reggiano, melted mozzarella, and garlic herb butter.'
WHERE dish_id = 4;

UPDATE dishes
SET dish_name = 'Savora Royal Seafood Hotpot Set',
    description = 'Aromatic lemongrass broth accompanied by tiger prawns, squid, octopus, Manila clams, and wild mushrooms.'
WHERE dish_id = 5;

UPDATE dishes
SET dish_name = 'Belgian Molten Chocolate Lava Cake & Gelato',
    description = 'Warm 70% dark Belgian chocolate fondant with a liquid ganache center, paired with Madagascar vanilla gelato.'
WHERE dish_id = 6;

UPDATE dishes
SET dish_name = 'Savora Signature Peach, Orange & Lemongrass Brew',
    description = 'Cold-brewed artisanal black tea infused with crisp white peach slices, Valencia orange, and fresh lemongrass.'
WHERE dish_id = 7;

-- 5. Dining tables & zones (matches table_code, preserves live operational status)
UPDATE dining_tables
SET table_name = 'Table 01 — Window Side',
    area = '1st Floor',
    note = 'Romantic window-side table for two'
WHERE table_code = 'T1-B01';

UPDATE dining_tables
SET table_name = 'Table 02 — Main Hall',
    area = '1st Floor',
    note = 'Standard family booth (4 seats)'
WHERE table_code = 'T1-B02';

UPDATE dining_tables
SET table_name = 'Table 03 — Main Hall',
    area = '1st Floor',
    note = 'Cozy central dining table'
WHERE table_code = 'T1-B03';

UPDATE dining_tables
SET table_name = 'Table 04 — Main Hall',
    area = '1st Floor',
    note = 'Near live piano stage'
WHERE table_code = 'T1-B04';

UPDATE dining_tables
SET table_name = 'Table 01 — Garden Terrace',
    area = '2nd Floor',
    note = 'Reserved for evening dining slot'
WHERE table_code = 'T2-B01';

UPDATE dining_tables
SET table_name = 'Table 02 — Garden Terrace',
    area = '2nd Floor',
    note = 'Spacious group dining table overlooking terrace'
WHERE table_code = 'T2-B02';

UPDATE dining_tables
SET table_name = 'VIP Lotus Private Suite',
    area = 'VIP Zone',
    note = 'Private suite with projector & sound system'
WHERE table_code = 'VIP-01';

UPDATE dining_tables
SET table_name = 'VIP Royal Banquet Room',
    area = 'VIP Zone',
    note = 'Scheduled air-conditioning maintenance'
WHERE table_code = 'VIP-02';

-- 6. Vouchers
UPDATE vouchers
SET description = '10% OFF total dine-in bill (up to 100,000 VND for orders from 500,000 VND)'
WHERE voucher_code = 'SAVORA10';

UPDATE vouchers
SET description = '15% OFF first online table reservation (up to 150,000 VND for orders from 300,000 VND)'
WHERE voucher_code = 'WELCOME50';

UPDATE vouchers
SET description = '20% OFF VIP Private Suite booking for birthday & corporate parties (up to 300,000 VND)'
WHERE voucher_code = 'VIPPARTY20';

-- 7. Reservations preferred_area alignment
UPDATE reservations SET preferred_area = '1st Floor' WHERE preferred_area = 'Tầng 1';
UPDATE reservations SET preferred_area = '2nd Floor' WHERE preferred_area = 'Tầng 2';
UPDATE reservations SET preferred_area = 'VIP Zone' WHERE preferred_area = 'Khu VIP';

-- 8. Activity log seeded user display names
UPDATE user_activity_logs SET user_full_name = 'Admin Savora' WHERE user_full_name = 'Quản Trị Savora';
UPDATE user_activity_logs SET user_full_name = 'Minh Nguyen' WHERE user_full_name IN ('Nguyễn Văn Khách', 'Customer Nguyễn Minh');
UPDATE user_activity_logs SET user_full_name = 'Lan Tran' WHERE user_full_name = 'Trần Thị Phục Vụ';
UPDATE user_activity_logs SET user_full_name = 'Hoang Le' WHERE user_full_name = 'Lê Hoàng Bếp Trưởng';

-- 9. Guest feedback replied_by display name (preserves feedback messages and replies)
UPDATE guest_feedback SET replied_by = 'Admin Savora' WHERE replied_by = 'Quản Trị Savora';
