package vn.edu.fpt.savora.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.service.AdminPortalService;
import vn.edu.fpt.savora.service.CloudinaryUploadService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminPortalService adminPortalService;
    private final CloudinaryUploadService cloudinaryUploadService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getOverview() {
        return ApiResponse.ok(adminPortalService.getAdminDashboardOverview());
    }

    @PostMapping("/upload-image")
    public ApiResponse<Map<String, String>> uploadImageToCloudinary(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "savora/dishes") String folder
    ) {
        String secureUrl = cloudinaryUploadService.uploadImage(file, folder);
        return ApiResponse.ok("Uploaded image to Cloudinary!", Map.of("imageUrl", secureUrl));
    }

    @PostMapping("/dishes")
    public ApiResponse<Void> saveDish(@RequestBody Map<String, Object> payload) {
        adminPortalService.saveDish(payload);
        return ApiResponse.ok("Dish saved successfully!", null);
    }

    @PostMapping("/dishes/{dishId}/toggle")
    public ApiResponse<Void> toggleDish(@PathVariable Integer dishId) {
        adminPortalService.toggleDishStatus(dishId);
        return ApiResponse.ok("Updated dish availability status!", null);
    }

    @DeleteMapping("/dishes/{dishId}")
    public ApiResponse<Void> deleteDish(@PathVariable Integer dishId) {
        adminPortalService.deleteDish(dishId);
        return ApiResponse.ok("Deleted dish from menu!", null);
    }

    @PostMapping("/tables")
    public ApiResponse<Void> saveTable(@RequestBody Map<String, Object> payload) {
        adminPortalService.saveTable(payload);
        return ApiResponse.ok("Dining table saved successfully!", null);
    }

    @DeleteMapping("/tables/{tableId}")
    public ApiResponse<Void> deleteTable(@PathVariable Integer tableId) {
        adminPortalService.deleteTable(tableId);
        return ApiResponse.ok("Deleted dining table!", null);
    }

    @PostMapping("/vouchers")
    public ApiResponse<Void> saveVoucher(@RequestBody Map<String, Object> payload) {
        adminPortalService.saveVoucher(payload);
        return ApiResponse.ok("Promotional voucher saved successfully!", null);
    }

    @PostMapping("/vouchers/{voucherId}/toggle")
    public ApiResponse<Void> toggleVoucher(@PathVariable Integer voucherId) {
        adminPortalService.toggleVoucherStatus(voucherId);
        return ApiResponse.ok("Updated voucher status!", null);
    }

    @PostMapping("/users")
    public ApiResponse<Void> createUser(@RequestBody Map<String, Object> payload) {
        adminPortalService.createStaffOrUser(payload);
        return ApiResponse.ok("Created new account successfully!", null);
    }

    @PostMapping("/users/{userId}/toggle-status")
    public ApiResponse<Void> toggleUserStatus(@PathVariable Integer userId) {
        adminPortalService.toggleUserStatus(userId);
        return ApiResponse.ok("Updated account lock/active status!", null);
    }
}
