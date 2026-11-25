package vn.edu.fpt.savora.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.entity.*;
import vn.edu.fpt.savora.enums.*;
import vn.edu.fpt.savora.repository.*;
import vn.edu.fpt.savora.security.util.PasswordUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Automatic database seeder for cloud deployments (e.g. Render) when database starts empty.
 * Does nothing if tables and roles are already populated (e.g. local MySQL).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavoraDatabaseSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final DishRepository dishRepository;
    private final DiningTableRepository diningTableRepository;
    private final VoucherRepository voucherRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (diningTableRepository.count() > 0 && dishRepository.count() > 0) {
            return;
        }
        log.info("[SavoraDatabaseSeeder] Empty database detected — seeding default Savora Fine Dining data...");

        // 1. Roles (IDs 1..4: ADMIN, CUSTOMER, STAFF, CHEF)
        Role adminRole = ensureRole("ADMIN", "Restaurant Administrator & Manager");
        Role customerRole = ensureRole("CUSTOMER", "Online & Dine-in Customer");
        Role staffRole = ensureRole("STAFF", "Waiter & Cashier Service Staff");
        Role chefRole = ensureRole("CHEF", "Executive Kitchen Chef");

        // 2. Default Accounts (password: 123456)
        String hash = PasswordUtils.hashPassword("123456");
        User admin = ensureUser("Admin", "Savora", "admin@savora.vn", "0901000001", hash, adminRole);
        User customer = ensureUser("Minh", "Nguyen", "customer@savora.vn", "0901000002", hash, customerRole);
        User staff = ensureUser("Waiter", "Minh Anh", "staff@savora.vn", "0901000003", hash, staffRole);
        ensureUser("Chef", "Hoang Le", "chef@savora.vn", "0901000004", hash, chefRole);

        // 3. Categories
        Category catStarters = categoryRepository.save(Category.builder()
                .categoryName("Starters & Salads")
                .description("Appetizers, organic salads & artisan soups")
                .displayOrder(1).isActive(true).build());
        Category catSteaks = categoryRepository.save(Category.builder()
                .categoryName("Prime Steaks & Seafood")
                .description("Imported steaks & fresh ocean seafood")
                .displayOrder(2).isActive(true).build());
        Category catHotpot = categoryRepository.save(Category.builder()
                .categoryName("Hotpot & Grill")
                .description("Signature table-side hotpot and charcoal grill")
                .displayOrder(3).isActive(true).build());
        Category catDesserts = categoryRepository.save(Category.builder()
                .categoryName("Artisan Desserts")
                .description("Handcrafted European pastries & desserts")
                .displayOrder(4).isActive(true).build());
        Category catDrinks = categoryRepository.save(Category.builder()
                .categoryName("Wines & Cocktails")
                .description("Sommelier-selected wines & signature mocktails")
                .displayOrder(5).isActive(true).build());

        // 4. Dishes
        Dish dSalmon = dishRepository.save(Dish.builder()
                .category(catStarters)
                .dishName("Pan-Seared Norwegian Salmon Salad")
                .description("Pan-seared Norwegian salmon fillet served over organic Da Lat greens with passion fruit vinaigrette.")
                .price(BigDecimal.valueOf(175000))
                .discountPrice(BigDecimal.valueOf(149000))
                .thumbnailUrl("https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=900&q=80")
                .isSignature(true).preparationTimeMinutes(10).status(DishStatus.AVAILABLE).build());

        Dish dSteak = dishRepository.save(Dish.builder()
                .category(catSteaks)
                .dishName("Lava-Stone Grilled Australian Tenderloin (250g)")
                .description("Prime grass-fed Australian beef tenderloin grilled medium-rare, served with truffle mash and peppercorn jus.")
                .price(BigDecimal.valueOf(420000))
                .discountPrice(BigDecimal.valueOf(389000))
                .thumbnailUrl("https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=900&q=80")
                .isSignature(true).preparationTimeMinutes(18).status(DishStatus.AVAILABLE).build());

        dishRepository.save(Dish.builder()
                .category(catSteaks)
                .dishName("Oven-Baked Spiny Lobster with Mozzarella")
                .description("Whole Nha Trang spiny lobster baked with aged mozzarella, garlic herb butter, and artisanal sourdough.")
                .price(BigDecimal.valueOf(690000))
                .discountPrice(BigDecimal.valueOf(650000))
                .thumbnailUrl("https://images.unsplash.com/photo-1559737558-2f5a35f4523b?auto=format&fit=crop&w=900&q=80")
                .isSignature(true).preparationTimeMinutes(22).status(DishStatus.AVAILABLE).build());

        dishRepository.save(Dish.builder()
                .category(catHotpot)
                .dishName("Savora Royal Seafood Hotpot Set")
                .description("Rich 12-hour bone & saffron broth served with tiger prawns, abalone, US beef brisket, and wild mushrooms.")
                .price(BigDecimal.valueOf(490000))
                .discountPrice(BigDecimal.valueOf(450000))
                .thumbnailUrl("https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=900&q=80")
                .isSignature(true).preparationTimeMinutes(15).status(DishStatus.AVAILABLE).build());

        dishRepository.save(Dish.builder()
                .category(catStarters)
                .dishName("French Pumpkin Velouté & Black Truffle")
                .description("Creamy roasted butternut squash soup finished with Italian black truffle oil and toasted brioche croutons.")
                .price(BigDecimal.valueOf(115000))
                .thumbnailUrl("https://images.unsplash.com/photo-1547592166-23ac45744acd?auto=format&fit=crop&w=900&q=80")
                .isSignature(false).preparationTimeMinutes(8).status(DishStatus.AVAILABLE).build());

        dishRepository.save(Dish.builder()
                .category(catHotpot)
                .dishName("Smoky Rosemary BBQ Iberico Pork Ribs")
                .description("Slow-roasted Spanish Iberico ribs glazed with wild honey and rosemary smoky barbecue sauce.")
                .price(BigDecimal.valueOf(340000))
                .discountPrice(BigDecimal.valueOf(310000))
                .thumbnailUrl("https://images.unsplash.com/photo-1529193591184-b1d58069ecdd?auto=format&fit=crop&w=900&q=80")
                .isSignature(false).preparationTimeMinutes(20).status(DishStatus.AVAILABLE).build());

        dishRepository.save(Dish.builder()
                .category(catDesserts)
                .dishName("Madagascar Vanilla Crème Brûlée")
                .description("Classic French custard infused with Madagascar vanilla bean, topped with caramelized raw cane sugar.")
                .price(BigDecimal.valueOf(95000))
                .thumbnailUrl("https://images.unsplash.com/photo-1470124182917-cc6e71b22ecc?auto=format&fit=crop&w=900&q=80")
                .isSignature(false).preparationTimeMinutes(7).status(DishStatus.AVAILABLE).build());

        dishRepository.save(Dish.builder()
                .category(catDrinks)
                .dishName("Hibiscus Berry Sparkling Mocktail")
                .description("House-brewed hibiscus tea infused with fresh Da Lat strawberries, mint, and sparkling mineral water.")
                .price(BigDecimal.valueOf(79000))
                .thumbnailUrl("https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=900&q=80")
                .isSignature(false).preparationTimeMinutes(5).status(DishStatus.AVAILABLE).build());

        // 5. Dining Tables
        DiningTable t1 = diningTableRepository.save(DiningTable.builder()
                .tableCode("T1-B01").tableName("Table 01 — Window Side").area("1st Floor").capacity(2)
                .status(TableStatus.AVAILABLE).note("Romantic window-side table for two").build());
        diningTableRepository.save(DiningTable.builder()
                .tableCode("T1-B02").tableName("Table 02 — Main Hall").area("1st Floor").capacity(4)
                .status(TableStatus.AVAILABLE).note("Standard family booth (4 seats)").build());
        diningTableRepository.save(DiningTable.builder()
                .tableCode("T1-B03").tableName("Table 03 — Main Hall").area("1st Floor").capacity(4)
                .status(TableStatus.AVAILABLE).note("Cozy central dining table").build());
        diningTableRepository.save(DiningTable.builder()
                .tableCode("T1-B04").tableName("Table 04 — Main Hall").area("1st Floor").capacity(4)
                .status(TableStatus.AVAILABLE).note("Near live piano stage").build());
        DiningTable t2 = diningTableRepository.save(DiningTable.builder()
                .tableCode("T2-B01").tableName("Table 01 — Garden Terrace").area("2nd Floor").capacity(6)
                .status(TableStatus.RESERVED).note("Reserved for evening dining slot").build());
        diningTableRepository.save(DiningTable.builder()
                .tableCode("T2-B02").tableName("Table 02 — Garden Terrace").area("2nd Floor").capacity(8)
                .status(TableStatus.AVAILABLE).note("Spacious group dining table overlooking terrace").build());
        diningTableRepository.save(DiningTable.builder()
                .tableCode("VIP-01").tableName("VIP Lotus Private Suite").area("VIP Zone").capacity(12)
                .status(TableStatus.AVAILABLE).note("Private suite with projector & sound system").build());
        diningTableRepository.save(DiningTable.builder()
                .tableCode("VIP-02").tableName("VIP Royal Banquet Room").area("VIP Zone").capacity(16)
                .status(TableStatus.MAINTENANCE).note("Air-conditioning maintenance").build());

        // 6. Vouchers
        voucherRepository.save(Voucher.builder()
                .voucherCode("SAVORA10")
                .description("10% OFF total bill for online table reservations")
                .discountPercent(10)
                .maxDiscountAmount(BigDecimal.valueOf(200000))
                .minOrderValue(BigDecimal.valueOf(300000))
                .quantity(200)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusMonths(6))
                .status(VoucherStatus.ACTIVE).build());

        voucherRepository.save(Voucher.builder()
                .voucherCode("WELCOME50")
                .description("15% OFF first online table reservation (up to 150,000 VND for orders from 300,000 VND)")
                .discountPercent(15)
                .maxDiscountAmount(BigDecimal.valueOf(150000))
                .minOrderValue(BigDecimal.valueOf(300000))
                .quantity(150)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusMonths(6))
                .status(VoucherStatus.ACTIVE).build());

        Voucher vVip = voucherRepository.save(Voucher.builder()
                .voucherCode("VIPPARTY20")
                .description("20% OFF VIP Private Suite booking for birthday & corporate parties (up to 300,000 VND)")
                .discountPercent(20)
                .maxDiscountAmount(BigDecimal.valueOf(300000))
                .minOrderValue(BigDecimal.valueOf(500000))
                .quantity(80)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusMonths(6))
                .status(VoucherStatus.ACTIVE).build());

        // 7. Sample Reservation & Sample Paid Invoice for Admin Revenue Chart
        reservationRepository.save(Reservation.builder()
                .reservationCode("SAV-254251")
                .customer(customer)
                .customerName("Minh Nguyen")
                .customerPhone("0901000002")
                .customerEmail("customer@savora.vn")
                .diningTable(t2)
                .voucher(vVip)
                .preferredArea("2nd Floor")
                .occasion("Family Dining")
                .reservationDate(LocalDate.now())
                .reservationTime(LocalTime.of(19, 0))
                .durationMinutes(120)
                .endTime(LocalTime.of(21, 0))
                .adultCount(4).childCount(0).guestCount(4)
                .status(ReservationStatus.CONFIRMED).build());

        RestaurantOrder paidOrder = restaurantOrderRepository.save(RestaurantOrder.builder()
                .orderCode("ORD-SAV-993877")
                .customer(customer)
                .staff(staff)
                .diningTable(t1)
                .voucher(vVip)
                .orderType(OrderType.DINE_IN)
                .subtotalAmount(BigDecimal.valueOf(927000))
                .discountAmount(BigDecimal.valueOf(185400))
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(741600))
                .status(OrderStatus.PAID)
                .closedAt(LocalDateTime.now())
                .build());

        orderItemRepository.saveAll(List.of(
                OrderItem.builder().order(paidOrder).dish(dSteak).quantity(2).unitPrice(BigDecimal.valueOf(389000)).itemStatus(OrderItemStatus.SERVED).build(),
                OrderItem.builder().order(paidOrder).dish(dSalmon).quantity(1).unitPrice(BigDecimal.valueOf(149000)).itemStatus(OrderItemStatus.SERVED).build()
        ));

        paymentRepository.save(Payment.builder()
                .order(paidOrder)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PAID)
                .paidAmount(BigDecimal.valueOf(741600))
                .transactionRef("TXN-SAV-230653")
                .paidAt(LocalDateTime.now())
                .cashier(staff)
                .build());

        log.info("[SavoraDatabaseSeeder] Seeded Savora Fine Dining database successfully!");
    }

    private Role ensureRole(String name, String desc) {
        return roleRepository.findByRoleName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(name).description(desc).build()));
    }

    private User ensureUser(String first, String last, String email, String phone, String hash, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName(first)
                        .lastName(last)
                        .email(email)
                        .phone(phone)
                        .passwordHash(hash)
                        .gender(Gender.OTHER)
                        .status(UserStatus.ACTIVE)
                        .roles(Set.of(role))
                        .build()));
    }
}
