package vn.edu.fpt.savora.controller.staff;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.dto.request.QrOrderSubmitRequest;
import vn.edu.fpt.savora.dto.response.ChefKitchenItemResponse;
import vn.edu.fpt.savora.dto.response.QrTableSessionResponse;
import vn.edu.fpt.savora.dto.response.StaffTableCardResponse;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.enums.Gender;
import vn.edu.fpt.savora.repository.UserRepository;
import vn.edu.fpt.savora.service.QrOrderService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffTableApiController {

    private final QrOrderService qrOrderService;
    private final UserRepository userRepository;

    @GetMapping("/tables")
    public ApiResponse<List<StaffTableCardResponse>> getAllTables() {
        return ApiResponse.ok(qrOrderService.getAllTablesForStaffDashboard());
    }

    @GetMapping("/tables/{tableCode}/session")
    public ApiResponse<QrTableSessionResponse> getTableDetail(@PathVariable String tableCode) {
        return ApiResponse.ok(qrOrderService.getTableSession(tableCode));
    }

    @PostMapping("/tables/order")
    public ApiResponse<QrTableSessionResponse> addDishesByWaiter(
            @Valid @RequestBody QrOrderSubmitRequest request,
            HttpSession session
    ) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        QrTableSessionResponse updated = qrOrderService.submitQrOrder(request, userId);
        return ApiResponse.ok("Added dishes to table " + request.getTableCode() + "!", updated);
    }

    @DeleteMapping("/order-items/{orderItemId}")
    public ApiResponse<QrTableSessionResponse> removePendingItem(@PathVariable Integer orderItemId) {
        QrTableSessionResponse updated = qrOrderService.removePendingOrderItem(orderItemId);
        return ApiResponse.ok("Removed pending dish from order.", updated);
    }

    @PostMapping("/order-items/{orderItemId}/serve")
    public ApiResponse<QrTableSessionResponse> serveOrderItem(@PathVariable Integer orderItemId) {
        QrTableSessionResponse updated = qrOrderService.markOrderItemServed(orderItemId);
        return ApiResponse.ok("Marked dish as SERVED.", updated);
    }

    @PostMapping("/tables/{tableCode}/status")
    public ApiResponse<QrTableSessionResponse> updateTableStatus(
            @PathVariable String tableCode,
            @RequestBody Map<String, String> payload
    ) {
        String action = payload != null ? payload.get("action") : "";
        QrTableSessionResponse updated = qrOrderService.updateTableStatusByWaiter(tableCode, action);
        return ApiResponse.ok("Updated table " + tableCode + " status.", updated);
    }

    @PostMapping("/tables/{tableCode}/checkout")
    public ApiResponse<QrTableSessionResponse> checkoutTableBill(
            @PathVariable String tableCode,
            @RequestBody(required = false) Map<String, String> payload,
            HttpSession session
    ) {
        String paymentMethod = payload != null ? payload.get("paymentMethod") : "CASH";
        Integer cashierId = session != null ? (Integer) session.getAttribute("userId") : null;
        QrTableSessionResponse updated = qrOrderService.checkoutTableOrder(tableCode, paymentMethod, cashierId);
        return ApiResponse.ok("Bill paid & closed for Table " + tableCode + "!", updated);
    }

    @GetMapping("/kitchen/items")
    public ApiResponse<List<ChefKitchenItemResponse>> getKitchenQueue() {
        return ApiResponse.ok(qrOrderService.getChefKitchenQueue());
    }

    @PostMapping("/kitchen/items/{orderItemId}/status")
    public ApiResponse<ChefKitchenItemResponse> updateKitchenItemStatus(
            @PathVariable Integer orderItemId,
            @RequestBody Map<String, String> payload
    ) {
        String status = payload != null ? payload.get("status") : "";
        ChefKitchenItemResponse updated = qrOrderService.updateKitchenItemStatusByChef(orderItemId, status);
        return ApiResponse.ok("Dish status updated to " + updated.getItemStatus() + "!", updated);
    }

    @PostMapping("/profile")
    public ApiResponse<Map<String, Object>> updateWaiterProfile(
            @RequestBody Map<String, String> payload,
            HttpSession session
    ) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        if (userId == null) {
            throw new IllegalArgumentException("Please sign in to update your profile.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Staff account not found."));

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
