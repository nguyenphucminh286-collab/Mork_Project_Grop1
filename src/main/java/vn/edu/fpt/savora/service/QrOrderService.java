package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.dto.request.QrOrderSubmitRequest;
import vn.edu.fpt.savora.dto.response.ChefKitchenItemResponse;
import vn.edu.fpt.savora.dto.response.QrTableSessionResponse;
import vn.edu.fpt.savora.dto.response.StaffTableCardResponse;
import vn.edu.fpt.savora.entity.*;
import vn.edu.fpt.savora.enums.*;
import vn.edu.fpt.savora.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QrOrderService {

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(
            OrderStatus.OPEN,
            OrderStatus.PREPARING,
            OrderStatus.SERVED
    );

    private static final List<ReservationStatus> TODAY_ACTIVE_RES_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.SEATED
    );

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DiningTableRepository diningTableRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationRepository reservationRepository;
    private final DishRepository dishRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<StaffTableCardResponse> getAllTablesForStaffDashboard() {
        List<DiningTable> tables = diningTableRepository.findAllByOrderByTableCodeAsc();
        return tables.stream().map(table -> {
            Reservation todayRes = findTodayActiveReservation(table.getTableId());
            RestaurantOrder activeOrder = findActiveOrderSafely(table.getTableId());

            int totalItems = 0, pendingCount = 0, cookingCount = 0, readyCount = 0, servedCount = 0;
            if (activeOrder != null) {
                List<OrderItem> items = orderItemRepository.findByOrder_OrderIdOrderByCreatedAtDesc(activeOrder.getOrderId());
                for (OrderItem i : items) {
                    if (i.getItemStatus() == OrderItemStatus.CANCELLED) continue;
                    int qty = i.getQuantity() != null ? i.getQuantity() : 1;
                    totalItems += qty;
                    if (i.getItemStatus() == OrderItemStatus.PENDING) pendingCount += qty;
                    else if (i.getItemStatus() == OrderItemStatus.COOKING) cookingCount += qty;
                    else if (i.getItemStatus() == OrderItemStatus.READY) readyCount += qty;
                    else if (i.getItemStatus() == OrderItemStatus.SERVED) servedCount += qty;
                }
            }

            String timeSlot = "";
            if (todayRes != null && todayRes.getReservationTime() != null) {
                String start = todayRes.getReservationTime().format(TIME_FMT);
                String end = todayRes.getEndTime() != null
                        ? todayRes.getEndTime().format(TIME_FMT)
                        : todayRes.calculateEndTime().format(TIME_FMT);
                timeSlot = start + " – " + end;
            }

            return StaffTableCardResponse.builder()
                    .tableId(table.getTableId())
                    .tableCode(table.getTableCode())
                    .tableName(table.getTableName())
                    .area(table.getArea())
                    .capacity(table.getCapacity())
                    .status(table.getStatus().name())
                    .note(table.getNote())
                    .reservationId(todayRes != null ? todayRes.getReservationId() : null)
                    .reservationCode(todayRes != null ? todayRes.getReservationCode() : null)
                    .reservedCustomerName(todayRes != null ? todayRes.getCustomerName() : null)
                    .reservedCustomerPhone(todayRes != null ? todayRes.getCustomerPhone() : null)
                    .reservedTimeSlot(timeSlot)
                    .reservedGuestCount(todayRes != null ? todayRes.getGuestCount() : null)
                    .reservationStatus(todayRes != null ? todayRes.getStatus().name() : null)
                    .orderId(activeOrder != null ? activeOrder.getOrderId() : null)
                    .orderCode(activeOrder != null ? activeOrder.getOrderCode() : null)
                    .totalAmount(activeOrder != null ? activeOrder.getTotalAmount() : BigDecimal.ZERO)
                    .totalItemsCount(totalItems)
                    .pendingItemsCount(pendingCount)
                    .cookingItemsCount(cookingCount)
                    .readyItemsCount(readyCount)
                    .servedItemsCount(servedCount)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QrTableSessionResponse getTableSession(String tableCode) {
        DiningTable table = resolveTable(tableCode);
        Reservation todayRes = findTodayActiveReservation(table.getTableId());
        RestaurantOrder activeOrder = findActiveOrderSafely(table.getTableId());
        return buildSessionResponse(table, todayRes, activeOrder);
    }

    @Transactional
    public QrTableSessionResponse submitQrOrder(QrOrderSubmitRequest req, Integer sessionUserId) {
        String code = req.getTableCode() != null ? req.getTableCode().trim() : "T1-B01";
        DiningTable table = diningTableRepository.findByTableCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("Dining table " + code + " not found."));

        if (table.getStatus() == TableStatus.MAINTENANCE) {
            throw new IllegalArgumentException("Table " + table.getTableCode() + " is currently under maintenance.");
        }
        if (table.getStatus() == TableStatus.CLEANING) {
            throw new IllegalArgumentException("Table " + table.getTableCode() + " is currently being sanitized by staff. Please wait a moment.");
        }

        Reservation todayRes = findTodayActiveReservation(table.getTableId());
        if (todayRes != null && todayRes.getStatus() != ReservationStatus.SEATED) {
            todayRes.setStatus(ReservationStatus.SEATED);
            reservationRepository.save(todayRes);
        }

        if (table.getStatus() != TableStatus.OCCUPIED) {
            table.setStatus(TableStatus.OCCUPIED);
            diningTableRepository.save(table);
        }

        RestaurantOrder order = findActiveOrderSafely(table.getTableId());

        User customer = null;
        if (sessionUserId != null) {
            customer = userRepository.findById(sessionUserId).orElse(null);
        } else if (todayRes != null && todayRes.getCustomer() != null) {
            customer = todayRes.getCustomer();
        }

        Voucher voucher = null;
        if (req.getVoucherCode() != null && !req.getVoucherCode().isBlank()) {
            voucher = voucherRepository.findByVoucherCodeIgnoreCase(req.getVoucherCode().trim()).orElse(null);
        } else if (order != null && order.getVoucher() != null) {
            voucher = order.getVoucher();
        } else if (todayRes != null && todayRes.getVoucher() != null) {
            voucher = todayRes.getVoucher();
        }

        if (order == null) {
            String orderCode = "ORD-SAV-" + ThreadLocalRandom.current().nextInt(100000, 999999);
            order = RestaurantOrder.builder()
                    .orderCode(orderCode)
                    .customer(customer)
                    .diningTable(table)
                    .reservation(todayRes)
                    .voucher(voucher)
                    .orderType(OrderType.DINE_IN)
                    .subtotalAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .status(OrderStatus.OPEN)
                    .note(req.getOrderNote())
                    .build();
            order = restaurantOrderRepository.saveAndFlush(order);
        } else {
            if (voucher != null) {
                order.setVoucher(voucher);
            }
            if (customer != null && order.getCustomer() == null) {
                order.setCustomer(customer);
            }
            if (req.getOrderNote() != null && !req.getOrderNote().isBlank()) {
                order.setNote(req.getOrderNote().trim());
            }
            order = restaurantOrderRepository.saveAndFlush(order);
        }

        for (QrOrderSubmitRequest.QrCartItemRequest itemReq : req.getItems()) {
            Dish dish = dishRepository.findById(itemReq.getDishId())
                    .orElseThrow(() -> new IllegalArgumentException("Dish #" + itemReq.getDishId() + " not found."));
            if (dish.getStatus() != DishStatus.AVAILABLE) {
                throw new IllegalArgumentException("Dish '" + dish.getDishName() + "' is currently unavailable.");
            }
            BigDecimal unitPrice = dish.getDiscountPrice() != null ? dish.getDiscountPrice() : dish.getPrice();
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .dish(dish)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .note(itemReq.getNote() != null && !itemReq.getNote().isBlank() ? itemReq.getNote().trim() : null)
                    .itemStatus(OrderItemStatus.PENDING)
                    .build();
            orderItemRepository.save(orderItem);
        }
        orderItemRepository.flush();

        recalculateOrderTotals(order);
        RestaurantOrder savedOrder = restaurantOrderRepository.saveAndFlush(order);

        return buildSessionResponse(table, todayRes, savedOrder);
    }

    /**
     * Waiter (STAFF) removes a dish that is still in PENDING status if the guest changes their mind
     */
    @Transactional
    public QrTableSessionResponse removePendingOrderItem(Integer orderItemId) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Ordered dish item #" + orderItemId + " not found."));

        if (item.getItemStatus() != OrderItemStatus.PENDING) {
            throw new IllegalArgumentException("Cannot remove '" + item.getDish().getDishName()
                    + "' because its status is already " + item.getItemStatus() + ". Only PENDING dishes can be removed.");
        }

        RestaurantOrder order = item.getOrder();
        DiningTable table = order.getDiningTable();

        orderItemRepository.delete(item);
        orderItemRepository.flush();

        recalculateOrderTotals(order);
        RestaurantOrder savedOrder = restaurantOrderRepository.saveAndFlush(order);

        Reservation todayRes = table != null ? findTodayActiveReservation(table.getTableId()) : null;
        return buildSessionResponse(table, todayRes, savedOrder);
    }

    /**
     * Waiter (STAFF) marks a dish as SERVED after bringing it to the guest's table
     */
    @Transactional
    public QrTableSessionResponse markOrderItemServed(Integer orderItemId) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Ordered dish item #" + orderItemId + " not found."));

        if (item.getItemStatus() != OrderItemStatus.READY) {
            throw new IllegalArgumentException("Only READY dishes (finished by the Chef) can be marked as SERVED.");
        }

        item.setItemStatus(OrderItemStatus.SERVED);
        orderItemRepository.saveAndFlush(item);

        RestaurantOrder order = item.getOrder();
        DiningTable table = order.getDiningTable();
        Reservation todayRes = table != null ? findTodayActiveReservation(table.getTableId()) : null;
        return buildSessionResponse(table, todayRes, order);
    }

    /**
     * Waiter (STAFF) updates table operational status (SEAT_GUEST, CLOSE_TO_CLEANING, SET_AVAILABLE)
     */
    @Transactional
    public QrTableSessionResponse updateTableStatusByWaiter(String tableCode, String action) {
        DiningTable table = diningTableRepository.findByTableCodeIgnoreCase(tableCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Table " + tableCode + " not found."));

        Reservation todayRes = findTodayActiveReservation(table.getTableId());
        RestaurantOrder activeOrder = findActiveOrderSafely(table.getTableId());

        if ("SEAT_GUEST".equalsIgnoreCase(action)) {
            table.setStatus(TableStatus.OCCUPIED);
            diningTableRepository.saveAndFlush(table);
            if (todayRes != null && todayRes.getStatus() != ReservationStatus.SEATED) {
                todayRes.setStatus(ReservationStatus.SEATED);
                reservationRepository.saveAndFlush(todayRes);
            }
        } else if ("CLOSE_TO_CLEANING".equalsIgnoreCase(action)) {
            if (activeOrder != null) {
                activeOrder.setStatus(OrderStatus.CLOSED);
                activeOrder.setClosedAt(LocalDateTime.now());
                restaurantOrderRepository.saveAndFlush(activeOrder);
                activeOrder = null;
            }
            if (todayRes != null && todayRes.getStatus() == ReservationStatus.SEATED) {
                todayRes.setStatus(ReservationStatus.COMPLETED);
                reservationRepository.saveAndFlush(todayRes);
                todayRes = null;
            }
            table.setStatus(TableStatus.CLEANING);
            diningTableRepository.saveAndFlush(table);
        } else if ("SET_AVAILABLE".equalsIgnoreCase(action)) {
            table.setStatus(TableStatus.AVAILABLE);
            diningTableRepository.saveAndFlush(table);
        }

        return buildSessionResponse(table, todayRes, activeOrder);
    }

    /**
     * Waiter (STAFF) generates final bill, records payment in payments table, marks order PAID, and sets table to CLEANING
     */
    @Transactional
    public QrTableSessionResponse checkoutTableOrder(String tableCode, String paymentMethodStr, Integer cashierUserId) {
        DiningTable table = diningTableRepository.findByTableCodeIgnoreCase(tableCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Table " + tableCode + " not found."));

        RestaurantOrder activeOrder = findActiveOrderSafely(table.getTableId());
        if (activeOrder == null) {
            throw new IllegalArgumentException("No active unpaid order found for Table " + tableCode + ".");
        }

        List<OrderItem> items = orderItemRepository.findByOrder_OrderIdOrderByCreatedAtDesc(activeOrder.getOrderId());
        List<OrderItem> billableItems = items.stream()
                .filter(i -> i.getItemStatus() != OrderItemStatus.CANCELLED)
                .collect(Collectors.toList());

        if (billableItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot checkout an empty order with no dishes.");
        }

        boolean hasUnserved = billableItems.stream()
                .anyMatch(i -> i.getItemStatus() != OrderItemStatus.SERVED);
        if (hasUnserved) {
            throw new IllegalArgumentException("All dishes must be marked as SERVED (or removed if PENDING) before completing bill checkout.");
        }

        recalculateOrderTotals(activeOrder);

        PaymentMethod method = PaymentMethod.CASH;
        if (paymentMethodStr != null && !paymentMethodStr.isBlank()) {
            try {
                method = PaymentMethod.valueOf(paymentMethodStr.trim().toUpperCase());
            } catch (Exception ignored) {
                method = PaymentMethod.CASH;
            }
        }

        User cashier = cashierUserId != null ? userRepository.findById(cashierUserId).orElse(null) : null;

        Payment payment = paymentRepository.findByOrder_OrderId(activeOrder.getOrderId())
                .orElseGet(Payment::new);
        payment.setOrder(activeOrder);
        payment.setPaymentMethod(method);
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAmount(activeOrder.getTotalAmount());
        payment.setTransactionRef("TXN-SAV-" + ThreadLocalRandom.current().nextInt(100000, 999999));
        payment.setPaidAt(LocalDateTime.now());
        payment.setCashier(cashier);
        paymentRepository.saveAndFlush(payment);

        activeOrder.setStatus(OrderStatus.PAID);
        activeOrder.setClosedAt(LocalDateTime.now());
        if (cashier != null) {
            activeOrder.setStaff(cashier);
        }
        restaurantOrderRepository.saveAndFlush(activeOrder);

        Reservation todayRes = findTodayActiveReservation(table.getTableId());
        if (todayRes != null && todayRes.getStatus() == ReservationStatus.SEATED) {
            todayRes.setStatus(ReservationStatus.COMPLETED);
            reservationRepository.saveAndFlush(todayRes);
            todayRes = null;
        }

        table.setStatus(TableStatus.CLEANING);
        diningTableRepository.saveAndFlush(table);

        return buildSessionResponse(table, todayRes, null);
    }

    /**
     * Chef (CHEF) fetches all ordered items across active tables for the Kitchen Display System
     */
    @Transactional(readOnly = true)
    public List<ChefKitchenItemResponse> getChefKitchenQueue() {
        List<OrderItemStatus> kitchenStatuses = List.of(
                OrderItemStatus.PENDING,
                OrderItemStatus.COOKING,
                OrderItemStatus.READY,
                OrderItemStatus.SERVED
        );
        return orderItemRepository.findKitchenQueueItems(kitchenStatuses, ACTIVE_ORDER_STATUSES)
                .stream()
                .map(this::toChefKitchenItemResponse)
                .collect(Collectors.toList());
    }

    /**
     * Chef (CHEF) updates dish status: PENDING -> COOKING -> READY
     */
    @Transactional
    public ChefKitchenItemResponse updateKitchenItemStatusByChef(Integer orderItemId, String targetStatus) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Ordered dish item #" + orderItemId + " not found."));

        OrderItemStatus newStatus;
        try {
            newStatus = OrderItemStatus.valueOf(targetStatus.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid kitchen item status: " + targetStatus);
        }

        if (newStatus != OrderItemStatus.COOKING && newStatus != OrderItemStatus.READY) {
            throw new IllegalArgumentException("Chef can only set dish status to COOKING or READY.");
        }

        item.setItemStatus(newStatus);
        OrderItem savedItem = orderItemRepository.saveAndFlush(item);

        RestaurantOrder order = savedItem.getOrder();
        if (order != null && order.getStatus() == OrderStatus.OPEN && newStatus == OrderItemStatus.COOKING) {
            order.setStatus(OrderStatus.PREPARING);
            restaurantOrderRepository.saveAndFlush(order);
        }

        return toChefKitchenItemResponse(savedItem);
    }

    private ChefKitchenItemResponse toChefKitchenItemResponse(OrderItem i) {
        RestaurantOrder o = i.getOrder();
        DiningTable t = o != null ? o.getDiningTable() : null;
        Dish d = i.getDish();
        return ChefKitchenItemResponse.builder()
                .orderItemId(i.getOrderItemId())
                .orderId(o != null ? o.getOrderId() : null)
                .orderCode(o != null ? o.getOrderCode() : "")
                .tableCode(t != null ? t.getTableCode() : "TAKEAWAY")
                .tableName(t != null ? t.getTableName() : "Takeaway")
                .area(t != null ? t.getArea() : "")
                .dishId(d != null ? d.getDishId() : null)
                .dishName(d != null ? d.getDishName() : "")
                .categoryName(d != null && d.getCategory() != null ? d.getCategory().getCategoryName() : "")
                .thumbnailUrl(d != null ? d.getThumbnailUrl() : "")
                .prepTimeMinutes(d != null ? d.getPreparationTimeMinutes() : 15)
                .quantity(i.getQuantity())
                .note(i.getNote())
                .orderNote(o != null ? o.getNote() : null)
                .itemStatus(i.getItemStatus().name())
                .orderedAt(i.getCreatedAt() != null ? i.getCreatedAt().format(TIME_FMT) : "")
                .build();
    }

    private RestaurantOrder findActiveOrderSafely(Integer tableId) {
        try {
            return restaurantOrderRepository
                    .findFirstByDiningTable_TableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                            tableId, OrderType.DINE_IN, ACTIVE_ORDER_STATUSES
                    )
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private void recalculateOrderTotals(RestaurantOrder order) {
        List<OrderItem> items = orderItemRepository.findByOrder_OrderIdOrderByCreatedAtDesc(order.getOrderId());
        BigDecimal subtotal = items.stream()
                .filter(i -> i.getItemStatus() != OrderItemStatus.CANCELLED)
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        Voucher v = order.getVoucher();
        if (v != null && v.getDiscountPercent() != null && v.getDiscountPercent() > 0) {
            discount = subtotal.multiply(BigDecimal.valueOf(v.getDiscountPercent()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }

        BigDecimal afterDiscount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setTotalAmount(afterDiscount);
    }

    private DiningTable resolveTable(String tableCode) {
        if (tableCode != null && !tableCode.isBlank()) {
            return diningTableRepository.findByTableCodeIgnoreCase(tableCode.trim())
                    .orElseGet(this::getFirstFallbackTable);
        }
        return getFirstFallbackTable();
    }

    private DiningTable getFirstFallbackTable() {
        return diningTableRepository.findAllByOrderByTableCodeAsc().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dining tables configured in the database."));
    }

    private Reservation findTodayActiveReservation(Integer tableId) {
        try {
            List<Reservation> list = reservationRepository.findActiveByTableAndDate(
                    tableId, LocalDate.now(), TODAY_ACTIVE_RES_STATUSES
            );
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private QrTableSessionResponse buildSessionResponse(
            DiningTable table,
            Reservation todayRes,
            RestaurantOrder activeOrder
    ) {
        List<QrTableSessionResponse.QrOrderedItemDto> placedItems = List.of();
        if (activeOrder != null) {
            placedItems = orderItemRepository.findByOrder_OrderIdOrderByCreatedAtDesc(activeOrder.getOrderId())
                    .stream()
                    .map(i -> QrTableSessionResponse.QrOrderedItemDto.builder()
                            .orderItemId(i.getOrderItemId())
                            .dishId(i.getDish().getDishId())
                            .dishName(i.getDish().getDishName())
                            .thumbnailUrl(i.getDish().getThumbnailUrl())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .lineTotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                            .note(i.getNote())
                            .itemStatus(i.getItemStatus().name())
                            .orderedAt(i.getCreatedAt() != null ? i.getCreatedAt().format(TIME_FMT) : "")
                            .build())
                    .collect(Collectors.toList());
        }

        Voucher resVoucher = todayRes != null ? todayRes.getVoucher() : null;
        Voucher orderVoucher = activeOrder != null ? activeOrder.getVoucher() : resVoucher;

        String timeSlot = "";
        if (todayRes != null && todayRes.getReservationTime() != null) {
            String start = todayRes.getReservationTime().format(TIME_FMT);
            String end = todayRes.getEndTime() != null
                    ? todayRes.getEndTime().format(TIME_FMT)
                    : todayRes.calculateEndTime().format(TIME_FMT);
            timeSlot = start + " – " + end;
        }

        return QrTableSessionResponse.builder()
                .tableId(table.getTableId())
                .tableCode(table.getTableCode())
                .tableName(table.getTableName())
                .area(table.getArea())
                .capacity(table.getCapacity())
                .tableStatus(table.getStatus().name())
                .tableNote(table.getNote())
                .reservationId(todayRes != null ? todayRes.getReservationId() : null)
                .reservationCode(todayRes != null ? todayRes.getReservationCode() : null)
                .reservedCustomerName(todayRes != null ? todayRes.getCustomerName() : null)
                .reservedTimeSlot(timeSlot)
                .reservedGuestCount(todayRes != null ? todayRes.getGuestCount() : null)
                .reservedVoucherCode(resVoucher != null ? resVoucher.getVoucherCode() : null)
                .reservedDiscountPercent(resVoucher != null ? resVoucher.getDiscountPercent() : null)
                .orderId(activeOrder != null ? activeOrder.getOrderId() : null)
                .orderCode(activeOrder != null ? activeOrder.getOrderCode() : null)
                .orderStatus(activeOrder != null ? activeOrder.getStatus().name() : null)
                .appliedVoucherCode(orderVoucher != null ? orderVoucher.getVoucherCode() : null)
                .appliedDiscountPercent(orderVoucher != null ? orderVoucher.getDiscountPercent() : null)
                .subtotalAmount(activeOrder != null ? activeOrder.getSubtotalAmount() : BigDecimal.ZERO)
                .discountAmount(activeOrder != null ? activeOrder.getDiscountAmount() : BigDecimal.ZERO)
                .taxAmount(activeOrder != null ? activeOrder.getTaxAmount() : BigDecimal.ZERO)
                .totalAmount(activeOrder != null ? activeOrder.getTotalAmount() : BigDecimal.ZERO)
                .placedItems(placedItems)
                .build();
    }
}
