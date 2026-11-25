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
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.enums.Gender;
import vn.edu.fpt.savora.repository.UserRepository;
import vn.edu.fpt.savora.service.ReservationService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CustomerReservationApiController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

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

    @PostMapping("/api/customer/profile")
    public ApiResponse<Map<String, Object>> updateCustomerProfile(
            @RequestBody Map<String, String> payload,
            HttpSession session
    ) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        if (userId == null) {
            throw new IllegalArgumentException("Please sign in to update your profile.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Customer account not found."));

        if (payload.get("firstName") != null && !payload.get("firstName").isBlank()) {
            user.setFirstName(payload.get("firstName").trim());
        }
        if (payload.get("lastName") != null && !payload.get("lastName").isBlank()) {
            user.setLastName(payload.get("lastName").trim());
        }
        if (payload.get("phone") != null) {
            user.setPhone(payload.get("phone").trim());
        }
        if (payload.get("address") != null) {
            user.setAddress(payload.get("address").trim());
        }
        if (payload.get("gender") != null) {
            try {
                user.setGender(Gender.valueOf(payload.get("gender").trim().toUpperCase()));
            } catch (Exception ignored) {
            }
        }

        userRepository.save(user);
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        session.setAttribute("fullName", fullName);

        return ApiResponse.ok("Profile updated successfully!", Map.of("fullName", fullName));
    }
}
