package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.dto.request.PasGoReservationRequest;
import vn.edu.fpt.savora.dto.response.DishResponse;
import vn.edu.fpt.savora.dto.response.ReservationDetailResponse;
import vn.edu.fpt.savora.dto.response.TableAvailabilityResponse;
import vn.edu.fpt.savora.entity.DiningTable;
import vn.edu.fpt.savora.entity.Dish;
import vn.edu.fpt.savora.entity.Reservation;
import vn.edu.fpt.savora.entity.RestaurantOrder;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.enums.DishStatus;
import vn.edu.fpt.savora.enums.OrderStatus;
import vn.edu.fpt.savora.enums.OrderType;
import vn.edu.fpt.savora.enums.ReservationStatus;
import vn.edu.fpt.savora.enums.TableStatus;
import vn.edu.fpt.savora.enums.VoucherStatus;
import vn.edu.fpt.savora.repository.DiningTableRepository;
import vn.edu.fpt.savora.repository.DishRepository;
import vn.edu.fpt.savora.repository.ReservationRepository;
import vn.edu.fpt.savora.repository.RestaurantOrderRepository;
import vn.edu.fpt.savora.repository.UserRepository;
import vn.edu.fpt.savora.repository.VoucherRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_CONFLICT_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.SEATED
    );

    private static final List<OrderStatus> ACTIVE_DINE_IN_ORDER_STATUSES = List.of(
            OrderStatus.OPEN,
            OrderStatus.PREPARING,
            OrderStatus.SERVED
    );

    /**
     * Standard dining limit: 120 minutes (2 hours) + 10 minutes table cleaning turnaround = 130 minutes total
     */
    private static final int DINING_DURATION_MINUTES = 120;
    private static final int CLEANING_BUFFER_MINUTES = 10;
    private static final int TOTAL_TURNAROUND_MINUTES = DINING_DURATION_MINUTES + CLEANING_BUFFER_MINUTES;

    private static final LocalTime OPEN_BOOKING_TIME = LocalTime.of(10, 30);
    private static final LocalTime LAST_BOOKING_TIME = LocalTime.of(21, 0);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DiningTableRepository diningTableRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final DishRepository dishRepository;

    public List<Voucher> getActiveVouchers() {
        return voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<DishResponse> getAvailableDishesFromDb() {
        return dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE)
                .stream()
                .map(this::toDishResponse)
                .collect(Collectors.toList());
    }

    private DishResponse toDishResponse(Dish d) {
        Integer discountPct = null;
        if (d.getDiscountPrice() != null && d.getPrice() != null
                && d.getPrice().compareTo(BigDecimal.ZERO) > 0
                && d.getDiscountPrice().compareTo(d.getPrice()) < 0) {
            discountPct = d.getPrice().subtract(d.getDiscountPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(d.getPrice(), 0, RoundingMode.HALF_UP)
                    .intValue();
        }
        return DishResponse.builder()
                .dishId(d.getDishId())
                .categoryId(d.getCategory() != null ? d.getCategory().getCategoryId() : null)
                .categoryName(d.getCategory() != null ? d.getCategory().getCategoryName() : "Other")
                .dishName(d.getDishName())
                .description(d.getDescription())
                .price(d.getPrice())
                .discountPrice(d.getDiscountPrice())
                .discountPercent(discountPct)
                .prepTime(d.getPreparationTimeMinutes())
                .isSignature(d.getIsSignature())
                .thumbnailUrl(d.getThumbnailUrl())
                .build();
    }

    /**
     * Real-time table availability evaluation for booking form
     */
    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> getRealTimeTableAvailability(
            LocalDate date,
            LocalTime time,
            int adultCount,
            int childCount,
            String area
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalTime targetTime = time != null ? time : LocalTime.of(19, 0);

        List<DiningTable> allTables = diningTableRepository.findAllByOrderByTableCodeAsc();
        final List<Reservation> dayReservations = loadDayReservationsSafely(targetDate);

        LocalDateTime now = LocalDateTime.now();

        return allTables.stream()
                .filter(t -> area == null || area.isBlank() || "ALL".equalsIgnoreCase(area) || t.getArea().equalsIgnoreCase(area))
                .map(table -> evaluateTableForSlot(table, targetDate, targetTime, dayReservations, now))
                .collect(Collectors.toList());
    }

    private List<Reservation> loadDayReservationsSafely(LocalDate targetDate) {
        try {
            return reservationRepository.findByReservationDateAndStatusIn(
                    targetDate, ACTIVE_CONFLICT_STATUSES
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    private TableAvailabilityResponse evaluateTableForSlot(
            DiningTable table,
            LocalDate targetDate,
            LocalTime targetTime,
            List<Reservation> dayReservations,
            LocalDateTime now
    ) {
        boolean bookable = true;
        String reason = null;
        String slotStatus = TableStatus.AVAILABLE.name();

        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        LocalTime requestedSlotEnd = targetTime.plusMinutes(TOTAL_TURNAROUND_MINUTES);

        // 1. MAINTENANCE tables are never bookable on any date
        if (table.getStatus() == TableStatus.MAINTENANCE) {
            bookable = false;
            slotStatus = TableStatus.MAINTENANCE.name();
            reason = "Table is under maintenance";
        }

        // 2. For TODAY only: evaluate live OCCUPIED (2h dining + 10m cleaning) and CLEANING (10m sanitization) windows
        if (bookable && targetDate.equals(today)) {
            if (table.getStatus() == TableStatus.CLEANING) {
                LocalTime cleaningDoneTime = nowTime.plusMinutes(CLEANING_BUFFER_MINUTES);
                if (targetTime.isBefore(cleaningDoneTime)) {
                    bookable = false;
                    slotStatus = TableStatus.CLEANING.name();
                    reason = "Sanitizing until " + cleaningDoneTime.format(TIME_FMT) + " (max 10m cleaning)";
                }
            } else if (table.getStatus() == TableStatus.OCCUPIED) {
                LocalTime occupiedStart = resolveCurrentOccupiedStartTime(table.getTableId(), dayReservations, nowTime);
                LocalTime occupiedFreeAt = occupiedStart.plusMinutes(TOTAL_TURNAROUND_MINUTES);
                // If current guests are still at the table beyond 2h, ensure at least 10m cleaning buffer from now
                if (occupiedFreeAt.isBefore(nowTime.plusMinutes(CLEANING_BUFFER_MINUTES))) {
                    occupiedFreeAt = nowTime.plusMinutes(CLEANING_BUFFER_MINUTES);
                }
                // Check if requested slot [targetTime, targetTime + 130m] overlaps with [occupiedStart, occupiedFreeAt]
                if (targetTime.isBefore(occupiedFreeAt) && occupiedStart.isBefore(requestedSlotEnd)) {
                    bookable = false;
                    slotStatus = TableStatus.OCCUPIED.name();
                    reason = "Occupied until " + occupiedFreeAt.format(TIME_FMT) + " (2h dining + 10m cleaning)";
                }
            }
        }

        // 3. Check overlapping reservations on targetDate (120m dining + 10m cleaning = 130m window)
        if (bookable) {
            for (Reservation res : dayReservations) {
                if (res.getDiningTable() == null || !res.getDiningTable().getTableId().equals(table.getTableId())) {
                    continue;
                }
                if (res.getStatus() == ReservationStatus.PENDING
                        && res.getPendingExpiresAt() != null
                        && res.getPendingExpiresAt().isBefore(now)) {
                    continue;
                }
                LocalTime resStart = res.getReservationTime();
                int resDiningMins = (res.getDurationMinutes() != null && res.getDurationMinutes() > 0)
                        ? res.getDurationMinutes()
                        : DINING_DURATION_MINUTES;
                LocalTime resDiningEnd = resStart.plusMinutes(resDiningMins);
                LocalTime resReadyForNextGuest = resDiningEnd.plusMinutes(CLEANING_BUFFER_MINUTES);

                // Overlap between [targetTime, targetTime + 130m] and [resStart, resReadyForNextGuest]
                if (targetTime.isBefore(resReadyForNextGuest) && resStart.isBefore(requestedSlotEnd)) {
                    bookable = false;
                    slotStatus = (targetDate.equals(today) && res.getStatus() == ReservationStatus.SEATED)
                            ? TableStatus.OCCUPIED.name()
                            : TableStatus.RESERVED.name();
                    reason = "Booked (" + resStart.format(TIME_FMT) + " – " + resDiningEnd.format(TIME_FMT)
                            + " + 10m cleaning until " + resReadyForNextGuest.format(TIME_FMT) + ")";
                    break;
                }
            }
        }

        return TableAvailabilityResponse.builder()
                .tableId(table.getTableId())
                .tableCode(table.getTableCode())
                .tableName(table.getTableName())
                .area(table.getArea())
                .capacity(table.getCapacity())
                .operationalStatus(slotStatus)
                .bookableForSlot(bookable)
                .unavailableReason(reason)
                .note(table.getNote())
                .build();
    }

    /**
     * Determine when the current dine-in session started for an OCCUPIED table today
     */
    private LocalTime resolveCurrentOccupiedStartTime(Integer tableId, List<Reservation> todayReservations, LocalTime nowTime) {
        try {
            RestaurantOrder activeOrder = restaurantOrderRepository
                    .findFirstByDiningTable_TableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                            tableId, OrderType.DINE_IN, ACTIVE_DINE_IN_ORDER_STATUSES
                    )
                    .orElse(null);
            if (activeOrder != null && activeOrder.getCreatedAt() != null
                    && activeOrder.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
                return activeOrder.getCreatedAt().toLocalTime();
            }
        } catch (Exception ignored) {
        }

        for (Reservation r : todayReservations) {
            if (r.getDiningTable() != null && tableId.equals(r.getDiningTable().getTableId())
                    && r.getStatus() == ReservationStatus.SEATED && r.getReservationTime() != null) {
                return r.getReservationTime();
            }
        }
        return nowTime;
    }

    /**
     * Create a reservation with PESSIMISTIC_WRITE row lock to prevent concurrent double-booking
     */
    @Transactional
    public ReservationDetailResponse createPasGoReservation(PasGoReservationRequest req, Integer customerId) {
        int adults = req.getAdultCount() != null ? req.getAdultCount() : 2;
        int children = req.getChildCount() != null ? req.getChildCount() : 0;
        int totalGuests = adults + children;

        LocalDate today = LocalDate.now();
        if (req.getReservationDate().isBefore(today)) {
            throw new IllegalArgumentException("Reservation date cannot be in the past.");
        }

        // Validate booking hours: 10:30 to 21:00
        LocalTime requestedTime = req.getReservationTime();
        if (requestedTime.isBefore(OPEN_BOOKING_TIME) || requestedTime.isAfter(LAST_BOOKING_TIME)) {
            throw new IllegalArgumentException("Reservations are only accepted between 10:30 and 21:00.");
        }

        // Prevent booking a time slot that has already passed today
        if (req.getReservationDate().equals(today) && !requestedTime.isAfter(LocalTime.now())) {
            throw new IllegalArgumentException("The time slot " + requestedTime.format(TIME_FMT)
                    + " has already passed today. Please select a later time slot.");
        }

        // Row-level lock on the selected DiningTable
        DiningTable table = diningTableRepository.findByIdForUpdate(req.getTableId())
                .orElseThrow(() -> new IllegalArgumentException("Selected dining table does not exist."));

        if (table.getStatus() == TableStatus.MAINTENANCE) {
            throw new IllegalArgumentException("Table " + table.getTableCode() + " is currently under maintenance.");
        }
        if (totalGuests > table.getCapacity()) {
            throw new IllegalArgumentException("Total party size (" + adults + " Adults + " + children + " Children = "
                    + totalGuests + " guests) exceeds the maximum capacity of " + table.getTableCode()
                    + " (" + table.getCapacity() + " seats).");
        }

        // Re-validate overlapping reservations and live OCCUPIED/CLEANING window on the same table and date
        List<Reservation> existingOnTable = reservationRepository.findActiveByTableAndDate(
                table.getTableId(), req.getReservationDate(), ACTIVE_CONFLICT_STATUSES
        );
        LocalDateTime now = LocalDateTime.now();
        LocalTime requestedSlotEnd = requestedTime.plusMinutes(TOTAL_TURNAROUND_MINUTES);

        if (req.getReservationDate().equals(today)) {
            LocalTime nowTime = now.toLocalTime();
            if (table.getStatus() == TableStatus.CLEANING) {
                LocalTime cleaningDoneTime = nowTime.plusMinutes(CLEANING_BUFFER_MINUTES);
                if (requestedTime.isBefore(cleaningDoneTime)) {
                    throw new IllegalArgumentException("Table " + table.getTableCode()
                            + " is currently being sanitized until " + cleaningDoneTime.format(TIME_FMT)
                            + ". Please select a time slot after " + cleaningDoneTime.format(TIME_FMT) + ".");
                }
            } else if (table.getStatus() == TableStatus.OCCUPIED) {
                LocalTime occupiedStart = resolveCurrentOccupiedStartTime(table.getTableId(), existingOnTable, nowTime);
                LocalTime occupiedFreeAt = occupiedStart.plusMinutes(TOTAL_TURNAROUND_MINUTES);
                if (occupiedFreeAt.isBefore(nowTime.plusMinutes(CLEANING_BUFFER_MINUTES))) {
                    occupiedFreeAt = nowTime.plusMinutes(CLEANING_BUFFER_MINUTES);
                }
                if (requestedTime.isBefore(occupiedFreeAt) && occupiedStart.isBefore(requestedSlotEnd)) {
                    throw new IllegalArgumentException("Table " + table.getTableCode()
                            + " is currently occupied until " + occupiedFreeAt.format(TIME_FMT)
                            + " (2h dining + 10m cleaning). Please choose a later time slot.");
                }
            }
        }

        for (Reservation existing : existingOnTable) {
            if (existing.getStatus() == ReservationStatus.PENDING
                    && existing.getPendingExpiresAt() != null
                    && existing.getPendingExpiresAt().isBefore(now)) {
                continue;
            }
            LocalTime resStart = existing.getReservationTime();
            int resDiningMins = (existing.getDurationMinutes() != null && existing.getDurationMinutes() > 0)
                    ? existing.getDurationMinutes()
                    : DINING_DURATION_MINUTES;
            LocalTime resDiningEnd = resStart.plusMinutes(resDiningMins);
            LocalTime resReadyForNextGuest = resDiningEnd.plusMinutes(CLEANING_BUFFER_MINUTES);

            if (requestedTime.isBefore(resReadyForNextGuest) && resStart.isBefore(requestedSlotEnd)) {
                throw new IllegalArgumentException("Table " + table.getTableCode() + " is already reserved between "
                        + resStart.format(TIME_FMT) + " and " + resDiningEnd.format(TIME_FMT)
                        + " (+10m cleaning until " + resReadyForNextGuest.format(TIME_FMT)
                        + "). Please choose another table or time slot.");
            }
        }

        User customer = null;
        if (customerId != null) {
            customer = userRepository.findById(customerId).orElse(null);
        }

        Voucher voucher = null;
        if (req.getVoucherId() != null) {
            voucher = voucherRepository.findById(req.getVoucherId()).orElse(null);
        }

        String reservationCode = "SAV-" + ThreadLocalRandom.current().nextInt(100000, 999999);
        LocalTime endTime = requestedTime.plusMinutes(Reservation.DEFAULT_DURATION_MINUTES);

        Reservation reservation = Reservation.builder()
                .reservationCode(reservationCode)
                .customer(customer)
                .customerName(req.getCustomerName().trim())
                .customerPhone(req.getCustomerPhone().trim())
                .customerEmail(req.getCustomerEmail() != null ? req.getCustomerEmail().trim() : null)
                .diningTable(table)
                .voucher(voucher)
                .preferredArea(req.getPreferredArea() != null && !req.getPreferredArea().isBlank() ? req.getPreferredArea() : table.getArea())
                .occasion(req.getOccasion())
                .reservationDate(req.getReservationDate())
                .reservationTime(requestedTime)
                .durationMinutes(Reservation.DEFAULT_DURATION_MINUTES)
                .endTime(endTime)
                .adultCount(adults)
                .childCount(children)
                .guestCount(totalGuests)
                .specialRequest(req.getSpecialRequest())
                .status(ReservationStatus.CONFIRMED)
                .build();

        Reservation saved = reservationRepository.save(reservation);

        if (req.getReservationDate().equals(today) && table.getStatus() == TableStatus.AVAILABLE) {
            table.setStatus(TableStatus.RESERVED);
            diningTableRepository.save(table);
        }

        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationDetailResponse> getCustomerReservations(Integer customerId) {
        if (customerId == null) {
            return List.of();
        }
        return reservationRepository.findByCustomer_UserIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toDetailResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReservationDetailResponse cancelCustomerReservation(Integer reservationId, Integer customerId, String cancelReason) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (customerId != null && reservation.getCustomer() != null
                && !reservation.getCustomer().getUserId().equals(customerId)) {
            throw new IllegalArgumentException("You do not have permission to cancel this reservation.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only PENDING or CONFIRMED reservations can be cancelled.");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelReason(cancelReason != null && !cancelReason.isBlank() ? cancelReason.trim() : "Cancelled by customer");
        Reservation saved = reservationRepository.save(reservation);

        DiningTable table = reservation.getDiningTable();
        if (table != null && table.getStatus() == TableStatus.RESERVED) {
            List<Reservation> remainingToday = reservationRepository.findActiveByTableAndDate(
                    table.getTableId(), LocalDate.now(), ACTIVE_CONFLICT_STATUSES
            );
            if (remainingToday.isEmpty()) {
                table.setStatus(TableStatus.AVAILABLE);
                diningTableRepository.save(table);
            }
        }

        return toDetailResponse(saved);
    }

    private ReservationDetailResponse toDetailResponse(Reservation r) {
        DiningTable t = r.getDiningTable();
        Voucher v = r.getVoucher();
        LocalTime end = r.getEndTime() != null ? r.getEndTime() : r.calculateEndTime();

        return ReservationDetailResponse.builder()
                .reservationId(r.getReservationId())
                .reservationCode(r.getReservationCode())
                .customerName(r.getCustomerName())
                .customerPhone(r.getCustomerPhone())
                .customerEmail(r.getCustomerEmail())
                .reservationDate(r.getReservationDate() != null ? r.getReservationDate().toString() : "")
                .reservationTime(r.getReservationTime() != null ? r.getReservationTime().format(TIME_FMT) : "")
                .endTime(end != null ? end.format(TIME_FMT) : "")
                .durationMinutes(r.getDurationMinutes())
                .adultCount(r.getAdultCount())
                .childCount(r.getChildCount())
                .guestCount(r.getGuestCount())
                .preferredArea(r.getPreferredArea() != null ? r.getPreferredArea() : (t != null ? t.getArea() : ""))
                .tableId(t != null ? t.getTableId() : null)
                .tableCode(t != null ? t.getTableCode() : "TBD")
                .tableName(t != null ? t.getTableName() : "Assigned upon arrival")
                .voucherCode(v != null ? v.getVoucherCode() : null)
                .discountPercent(v != null ? v.getDiscountPercent() : null)
                .occasion(r.getOccasion())
                .specialRequest(r.getSpecialRequest())
                .status(r.getStatus() != null ? r.getStatus().name() : "PENDING")
                .cancelReason(r.getCancelReason())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().format(DATETIME_FMT) : "")
                .build();
    }
}
