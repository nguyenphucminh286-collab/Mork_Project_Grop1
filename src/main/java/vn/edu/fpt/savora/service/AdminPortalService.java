package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.entity.*;
import vn.edu.fpt.savora.enums.*;
import vn.edu.fpt.savora.repository.*;
import vn.edu.fpt.savora.security.util.PasswordUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPortalService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantOrderRepository restaurantOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final DishRepository dishRepository;
    private final CategoryRepository categoryRepository;
    private final DiningTableRepository diningTableRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserActivityTrackingService userActivityTrackingService;

    @Transactional
    public Map<String, Object> getAdminDashboardOverview() {
        List<RestaurantOrder> allOrders = restaurantOrderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Payment> allPayments = paymentRepository.findAll();
        Map<Integer, Payment> paymentByOrderId = allPayments.stream()
                .filter(p -> p.getOrder() != null)
                .collect(Collectors.toMap(p -> p.getOrder().getOrderId(), p -> p, (a, b) -> a));

        LocalDate today = LocalDate.now();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal todayRevenue = BigDecimal.ZERO;
        int paidOrdersCount = 0;
        int activeOrdersCount = 0;

        List<Map<String, Object>> orderList = new ArrayList<>();
        for (RestaurantOrder o : allOrders) {
            boolean isPaidOrClosed = (o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.CLOSED);
            BigDecimal amt = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            if (isPaidOrClosed) {
                totalRevenue = totalRevenue.add(amt);
                paidOrdersCount++;
                LocalDateTime refTime = o.getClosedAt() != null ? o.getClosedAt() : o.getCreatedAt();
                if (refTime != null && refTime.toLocalDate().equals(today)) {
                    todayRevenue = todayRevenue.add(amt);
                }
            } else if (o.getStatus() == OrderStatus.OPEN || o.getStatus() == OrderStatus.PREPARING || o.getStatus() == OrderStatus.SERVED) {
                activeOrdersCount++;
            }

            List<OrderItem> items = orderItemRepository.findByOrder_OrderIdOrderByCreatedAtDesc(o.getOrderId());
            List<Map<String, Object>> itemDtos = new ArrayList<>();
            int totalQty = 0;
            for (OrderItem i : items) {
                if (i.getItemStatus() == OrderItemStatus.CANCELLED) continue;
                int q = i.getQuantity() != null ? i.getQuantity() : 1;
                totalQty += q;
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("dishName", i.getDish() != null ? i.getDish().getDishName() : "Dish");
                im.put("quantity", q);
                im.put("unitPrice", i.getUnitPrice());
                im.put("lineTotal", i.getUnitPrice().multiply(BigDecimal.valueOf(q)));
                im.put("itemStatus", i.getItemStatus().name());
                itemDtos.add(im);
            }

            String guestName = "Walk-in QR Guest";
            if (o.getCustomer() != null) {
                guestName = (o.getCustomer().getFirstName() + " " + o.getCustomer().getLastName()).trim();
            } else if (o.getReservation() != null && o.getReservation().getCustomerName() != null) {
                guestName = o.getReservation().getCustomerName();
            }

            Payment pay = paymentByOrderId.get(o.getOrderId());
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("orderId", o.getOrderId());
            om.put("orderCode", o.getOrderCode());
            om.put("tableCode", o.getDiningTable() != null ? o.getDiningTable().getTableCode() : "TAKEAWAY");
            om.put("tableName", o.getDiningTable() != null ? o.getDiningTable().getTableName() : "Takeaway");
            om.put("area", o.getDiningTable() != null ? o.getDiningTable().getArea() : "");
            om.put("guestName", guestName);
            om.put("reservationCode", o.getReservation() != null ? o.getReservation().getReservationCode() : null);
            om.put("voucherCode", o.getVoucher() != null ? o.getVoucher().getVoucherCode() : null);
            om.put("discountPercent", o.getVoucher() != null ? o.getVoucher().getDiscountPercent() : null);
            om.put("subtotalAmount", o.getSubtotalAmount());
            om.put("discountAmount", o.getDiscountAmount());
            om.put("totalAmount", o.getTotalAmount());
            om.put("status", o.getStatus().name());
            om.put("paymentMethod", pay != null ? pay.getPaymentMethod().name() : (isPaidOrClosed ? "CASH" : "UNPAID"));
            om.put("transactionRef", pay != null ? pay.getTransactionRef() : null);
            om.put("totalQty", totalQty);
            om.put("items", itemDtos);
            om.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().format(DT_FMT) : "");
            om.put("closedAt", o.getClosedAt() != null ? o.getClosedAt().format(DT_FMT) : "");
            orderList.add(om);
        }

        List<Dish> allDishes = dishRepository.findAll(Sort.by(Sort.Direction.ASC, "dishId"));
        List<Map<String, Object>> dishList = allDishes.stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("dishId", d.getDishId());
            dm.put("categoryId", d.getCategory() != null ? d.getCategory().getCategoryId() : null);
            dm.put("categoryName", d.getCategory() != null ? d.getCategory().getCategoryName() : "Other");
            dm.put("dishName", d.getDishName());
            dm.put("description", d.getDescription());
            dm.put("price", d.getPrice());
            dm.put("discountPrice", d.getDiscountPrice());
            dm.put("thumbnailUrl", d.getThumbnailUrl());
            dm.put("isSignature", d.getIsSignature());
            dm.put("prepTime", d.getPreparationTimeMinutes());
            dm.put("status", d.getStatus().name());
            return dm;
        }).collect(Collectors.toList());

        List<Map<String, Object>> categoryList = categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder")).stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("categoryId", c.getCategoryId());
            cm.put("categoryName", c.getCategoryName());
            return cm;
        }).collect(Collectors.toList());

        List<DiningTable> allTables = diningTableRepository.findAllByOrderByTableCodeAsc();
        List<Map<String, Object>> tableList = allTables.stream().map(t -> {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("tableId", t.getTableId());
            tm.put("tableCode", t.getTableCode());
            tm.put("tableName", t.getTableName());
            tm.put("area", t.getArea());
            tm.put("capacity", t.getCapacity());
            tm.put("status", t.getStatus().name());
            tm.put("note", t.getNote());
            return tm;
        }).collect(Collectors.toList());

        List<Voucher> allVouchers = voucherRepository.findAll(Sort.by(Sort.Direction.DESC, "voucherId"));
        List<Map<String, Object>> voucherList = allVouchers.stream().map(v -> {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("voucherId", v.getVoucherId());
            vm.put("voucherCode", v.getVoucherCode());
            vm.put("description", v.getDescription());
            vm.put("discountPercent", v.getDiscountPercent());
            vm.put("maxDiscountAmount", v.getMaxDiscountAmount());
            vm.put("minOrderValue", v.getMinOrderValue());
            vm.put("quantity", v.getQuantity());
            vm.put("startDate", v.getStartDate() != null ? v.getStartDate().toLocalDate().format(DATE_FMT) : "");
            vm.put("endDate", v.getEndDate() != null ? v.getEndDate().toLocalDate().format(DATE_FMT) : "");
            vm.put("status", v.getStatus().name());
            return vm;
        }).collect(Collectors.toList());

        List<User> allUsers = userRepository.findAll(Sort.by(Sort.Direction.ASC, "userId"));
        List<Map<String, Object>> userList = allUsers.stream().map(u -> {
            Role role = u.getRoles().stream().findFirst().orElse(null);
            Map<String, Object> um = new LinkedHashMap<>();
            um.put("userId", u.getUserId());
            um.put("firstName", u.getFirstName());
            um.put("lastName", u.getLastName());
            um.put("fullName", (u.getFirstName() + " " + u.getLastName()).trim());
            um.put("email", u.getEmail());
            um.put("phone", u.getPhone());
            um.put("roleId", role != null ? role.getRoleId() : 2);
            um.put("roleName", role != null ? role.getRoleName() : "CUSTOMER");
            um.put("status", u.getStatus().name());
            um.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().format(DT_FMT) : "");
            return um;
        }).collect(Collectors.toList());

        long totalReservations = reservationRepository.count();
        List<Reservation> allReservations = reservationRepository.findAll(
                Sort.by(Sort.Direction.DESC, "reservationDate", "reservationTime")
        );
        List<Map<String, Object>> reservationList = allReservations.stream().map(r -> {
            DiningTable t = r.getDiningTable();
            Voucher v = r.getVoucher();
            String slot = "";
            if (r.getReservationTime() != null) {
                String s = r.getReservationTime().format(TIME_FMT);
                String e = r.getEndTime() != null
                        ? r.getEndTime().format(TIME_FMT)
                        : r.calculateEndTime().format(TIME_FMT);
                slot = s + " – " + e;
            }
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("reservationId", r.getReservationId());
            rm.put("reservationCode", r.getReservationCode());
            rm.put("tableId", t != null ? t.getTableId() : null);
            rm.put("tableCode", t != null ? t.getTableCode() : "TBD");
            rm.put("tableName", t != null ? t.getTableName() : "Unassigned");
            rm.put("area", t != null ? t.getArea() : (r.getPreferredArea() != null ? r.getPreferredArea() : ""));
            rm.put("customerName", r.getCustomerName());
            rm.put("customerPhone", r.getCustomerPhone());
            rm.put("customerEmail", r.getCustomerEmail());
            rm.put("reservationDate", r.getReservationDate() != null ? r.getReservationDate().format(DATE_FMT) : "");
            rm.put("timeSlot", slot);
            rm.put("adultCount", r.getAdultCount());
            rm.put("childCount", r.getChildCount());
            rm.put("guestCount", r.getGuestCount());
            rm.put("occasion", r.getOccasion());
            rm.put("specialRequest", r.getSpecialRequest());
            rm.put("voucherCode", v != null ? v.getVoucherCode() : null);
            rm.put("discountPercent", v != null ? v.getDiscountPercent() : null);
            rm.put("status", r.getStatus() != null ? r.getStatus().name() : "PENDING");
            rm.put("cancelReason", r.getCancelReason());
            rm.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().format(DT_FMT) : "");
            return rm;
        }).collect(Collectors.toList());

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalRevenue", totalRevenue);
        kpis.put("todayRevenue", todayRevenue);
        kpis.put("paidOrdersCount", paidOrdersCount);
        kpis.put("activeOrdersCount", activeOrdersCount);
        kpis.put("totalOrdersCount", allOrders.size());
        kpis.put("totalReservationsCount", totalReservations);
        kpis.put("totalDishesCount", allDishes.size());
        kpis.put("availableDishesCount", allDishes.stream().filter(d -> d.getStatus() == DishStatus.AVAILABLE).count());
        kpis.put("totalTablesCount", allTables.size());
        kpis.put("availableTablesCount", allTables.stream().filter(t -> t.getStatus() == TableStatus.AVAILABLE).count());
        kpis.put("occupiedTablesCount", allTables.stream().filter(t -> t.getStatus() == TableStatus.OCCUPIED).count());
        kpis.put("totalUsersCount", allUsers.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpis", kpis);
        result.put("orders", orderList);
        result.put("reservations", reservationList);
        result.put("dishes", dishList);
        result.put("categories", categoryList);
        result.put("tables", tableList);
        result.put("vouchers", voucherList);
        result.put("users", userList);
        result.put("activityLogs", userActivityTrackingService.getRecentActivityLogs());
        return result;
    }

    @Transactional
    public void saveDish(Map<String, Object> req) {
        Integer dishId = parseInt(req.get("dishId"));
        String dishName = str(req.get("dishName"));
        if (dishName == null || dishName.isBlank()) {
            throw new IllegalArgumentException("Dish name is required.");
        }
        Integer categoryId = parseInt(req.get("categoryId"));
        Category category = (categoryId != null ? categoryRepository.findById(categoryId).orElse(null) : null);
        if (category == null) {
            category = categoryRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No dish category found."));
        }

        BigDecimal price = parseDecimal(req.get("price"), BigDecimal.valueOf(150000));
        BigDecimal discountPrice = req.get("discountPrice") != null && !str(req.get("discountPrice")).isBlank()
                ? parseDecimal(req.get("discountPrice"), null)
                : null;
        Integer prepTime = parseInt(req.get("prepTime"));
        if (prepTime == null || prepTime <= 0) prepTime = 15;
        boolean isSignature = Boolean.parseBoolean(String.valueOf(req.getOrDefault("isSignature", "false")));
        String statusStr = str(req.get("status"));
        DishStatus status = DishStatus.AVAILABLE;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = DishStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }

        Dish dish = (dishId != null)
                ? dishRepository.findById(dishId).orElseGet(Dish::new)
                : new Dish();

        dish.setCategory(category);
        dish.setDishName(dishName.trim());
        dish.setDescription(str(req.get("description")));
        dish.setPrice(price);
        dish.setDiscountPrice(discountPrice);
        String thumb = str(req.get("thumbnailUrl"));
        if (thumb == null || thumb.isBlank()) {
            thumb = "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=900&q=80";
        }
        dish.setThumbnailUrl(thumb.trim());
        dish.setPreparationTimeMinutes(prepTime);
        dish.setIsSignature(isSignature);
        dish.setStatus(status);
        dishRepository.saveAndFlush(dish);
    }

    @Transactional
    public void toggleDishStatus(Integer dishId) {
        Dish dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new IllegalArgumentException("Dish #" + dishId + " not found."));
        if (dish.getStatus() == DishStatus.AVAILABLE) {
            dish.setStatus(DishStatus.OUT_OF_STOCK);
        } else {
            dish.setStatus(DishStatus.AVAILABLE);
        }
        dishRepository.saveAndFlush(dish);
    }

    @Transactional
    public void deleteDish(Integer dishId) {
        Dish dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new IllegalArgumentException("Dish #" + dishId + " not found."));

        List<OrderItem> referencedItems = orderItemRepository.findByDish_DishId(dishId);
        boolean inActiveOrder = referencedItems.stream().anyMatch(i ->
                i.getOrder() != null
                        && (i.getOrder().getStatus() == OrderStatus.OPEN
                        || i.getOrder().getStatus() == OrderStatus.PREPARING
                        || i.getOrder().getStatus() == OrderStatus.SERVED)
        );
        if (inActiveOrder) {
            throw new IllegalArgumentException("Cannot delete dish '" + dish.getDishName()
                    + "' while it is part of an active open table order. Please close the order or set the dish to OUT_OF_STOCK.");
        }

        if (!referencedItems.isEmpty()) {
            orderItemRepository.deleteAll(referencedItems);
            orderItemRepository.flush();
        }
        dishRepository.delete(dish);
        dishRepository.flush();
    }

    @Transactional
    public void saveTable(Map<String, Object> req) {
        Integer tableId = parseInt(req.get("tableId"));
        String tableCode = str(req.get("tableCode"));
        String tableName = str(req.get("tableName"));
        String area = str(req.get("area"));
        if (tableCode == null || tableCode.isBlank() || tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table Code and Table Name are required.");
        }
        Integer capacity = parseInt(req.get("capacity"));
        if (capacity == null || capacity <= 0) capacity = 4;
        String statusStr = str(req.get("status"));
        TableStatus status = TableStatus.AVAILABLE;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = TableStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }

        DiningTable table = (tableId != null)
                ? diningTableRepository.findById(tableId).orElseGet(DiningTable::new)
                : diningTableRepository.findByTableCodeIgnoreCase(tableCode.trim()).orElseGet(DiningTable::new);

        table.setTableCode(tableCode.trim().toUpperCase());
        table.setTableName(tableName.trim());
        table.setArea(area != null && !area.isBlank() ? area.trim() : "1st Floor");
        table.setCapacity(capacity);
        table.setStatus(status);
        table.setNote(str(req.get("note")));
        diningTableRepository.saveAndFlush(table);
    }

    @Transactional
    public void deleteTable(Integer tableId) {
        DiningTable table = diningTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Dining table #" + tableId + " not found."));
        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new IllegalArgumentException("Cannot delete table '" + table.getTableCode()
                    + "' while guests are actively dining (OCCUPIED). Please close or checkout the table first.");
        }
        diningTableRepository.delete(table);
        diningTableRepository.flush();
    }

    @Transactional
    public void saveVoucher(Map<String, Object> req) {
        Integer voucherId = parseInt(req.get("voucherId"));
        String code = str(req.get("voucherCode"));
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Voucher code is required.");
        }
        Integer discountPercent = parseInt(req.get("discountPercent"));
        if (discountPercent == null || discountPercent <= 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Discount percent must be between 1 and 100.");
        }

        Voucher v = (voucherId != null)
                ? voucherRepository.findById(voucherId).orElseGet(Voucher::new)
                : voucherRepository.findByVoucherCodeIgnoreCase(code.trim()).orElseGet(Voucher::new);

        v.setVoucherCode(code.trim().toUpperCase());
        v.setDescription(str(req.get("description")));
        v.setDiscountPercent(discountPercent);
        v.setMaxDiscountAmount(parseDecimal(req.get("maxDiscountAmount"), BigDecimal.valueOf(300000)));
        v.setMinOrderValue(parseDecimal(req.get("minOrderValue"), BigDecimal.ZERO));
        Integer qty = parseInt(req.get("quantity"));
        v.setQuantity(qty != null && qty > 0 ? qty : 100);
        v.setStartDate(LocalDateTime.now().minusDays(1));
        v.setEndDate(LocalDateTime.now().plusMonths(6));
        v.setStatus(VoucherStatus.ACTIVE);
        voucherRepository.saveAndFlush(v);
    }

    @Transactional
    public void toggleVoucherStatus(Integer voucherId) {
        Voucher v = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher #" + voucherId + " not found."));
        if (v.getStatus() == VoucherStatus.ACTIVE) {
            v.setStatus(VoucherStatus.DISABLED);
        } else {
            v.setStatus(VoucherStatus.ACTIVE);
        }
        voucherRepository.saveAndFlush(v);
    }

    @Transactional
    public void createStaffOrUser(Map<String, Object> req) {
        String email = str(req.get("email"));
        String firstName = str(req.get("firstName"));
        String lastName = str(req.get("lastName"));
        String password = str(req.get("password"));
        String roleName = str(req.get("roleName"));

        if (email == null || email.isBlank() || firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name and Email are required.");
        }
        String normEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normEmail)) {
            throw new IllegalArgumentException("Email '" + normEmail + "' already exists.");
        }

        String targetRoleName = (roleName != null && !roleName.isBlank()) ? roleName.trim().toUpperCase() : "STAFF";
        Role role = roleRepository.findByRoleName(targetRoleName)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(targetRoleName).description(targetRoleName).build()));

        String rawPass = (password != null && !password.isBlank()) ? password.trim() : "123456";
        User user = User.builder()
                .firstName(firstName.trim())
                .lastName(lastName != null && !lastName.isBlank() ? lastName.trim() : "Savora")
                .email(normEmail)
                .phone(str(req.get("phone")))
                .passwordHash(PasswordUtils.hashPassword(rawPass))
                .gender(Gender.OTHER)
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(role)))
                .build();
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public void toggleUserStatus(Integer userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User #" + userId + " not found."));
        boolean isAdmin = u.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getRoleName()) || Integer.valueOf(1).equals(r.getRoleId()));
        if (isAdmin) {
            throw new IllegalArgumentException("Admin accounts cannot be locked.");
        }
        if (u.getStatus() == UserStatus.LOCKED) {
            u.setStatus(UserStatus.ACTIVE);
        } else {
            u.setStatus(UserStatus.LOCKED);
        }
        userRepository.saveAndFlush(u);
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o).trim() : null;
    }

    private Integer parseInt(Object o) {
        if (o == null) return null;
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? null : Integer.parseInt(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(Object o, BigDecimal fallback) {
        if (o == null) return fallback;
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? fallback : new BigDecimal(s);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
