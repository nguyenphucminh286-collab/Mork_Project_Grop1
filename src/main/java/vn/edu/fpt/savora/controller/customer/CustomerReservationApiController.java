package vn.edu.fpt.savora.controller.customer;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.dto.request.PasGoReservationRequest;
import vn.edu.fpt.savora.dto.response.DishResponse;
import vn.edu.fpt.savora.dto.response.ReservationDetailResponse;
import vn.edu.fpt.savora.dto.response.TableAvailabilityResponse;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.service.ReservationService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CustomerReservationApiController {

    private final ReservationService reservationService;

    @GetMapping("/api/public/pasgo/tables")
    public ApiResponse<List<TableAvailabilityResponse>> getAvailableTables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time,
            @RequestParam(defaultValue = "2") int adults,
            @RequestParam(defaultValue = "0") int children,
            @RequestParam(defaultValue = "ALL") String area
    ) {
        List<TableAvailabilityResponse> tables = reservationService.getRealTimeTableAvailability(
                date, time, adults, children, area
        );
        return ApiResponse.ok(tables);
    }

    @GetMapping("/api/public/pasgo/vouchers")
    public ApiResponse<List<Voucher>> getActiveVouchers() {
        return ApiResponse.ok(reservationService.getActiveVouchers());
    }

    @GetMapping("/api/public/pasgo/menu")
    public ApiResponse<List<DishResponse>> getPublicMenu() {
        return ApiResponse.ok(reservationService.getAvailableDishesFromDb());
    }

    @PostMapping("/api/customer/reservations")
    public ApiResponse<ReservationDetailResponse> createReservation(
            @Valid @RequestBody PasGoReservationRequest request,
            HttpSession session
    ) {
        Integer userId = (Integer) session.getAttribute("userId");
        ReservationDetailResponse created = reservationService.createPasGoReservation(request, userId);
        return ApiResponse.ok("Table reserved successfully!", created);
    }

    @GetMapping("/api/customer/reservations")
    public ApiResponse<List<ReservationDetailResponse>> getMyReservations(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        return ApiResponse.ok(reservationService.getCustomerReservations(userId));
    }

    @PostMapping("/api/customer/reservations/{reservationId}/cancel")
    public ApiResponse<ReservationDetailResponse> cancelReservation(
            @PathVariable Integer reservationId,
            @RequestBody(required = false) Map<String, String> payload,
            HttpSession session
    ) {
        Integer userId = (Integer) session.getAttribute("userId");
        String reason = payload != null ? payload.get("cancelReason") : null;
        ReservationDetailResponse cancelled = reservationService.cancelCustomerReservation(reservationId, userId, reason);
        return ApiResponse.ok("Reservation cancelled successfully.", cancelled);
    }
}
