package vn.edu.fpt.savora.controller.customer;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.dto.request.QrOrderSubmitRequest;
import vn.edu.fpt.savora.dto.response.QrTableSessionResponse;
import vn.edu.fpt.savora.service.QrOrderService;

@RestController
@RequestMapping("/api/public/qr")
@RequiredArgsConstructor
public class QrOrderApiController {

    private final QrOrderService qrOrderService;

    @GetMapping("/session")
    public ApiResponse<QrTableSessionResponse> getTableSession(
            @RequestParam(name = "table", defaultValue = "T1-B01") String tableCode
    ) {
        return ApiResponse.ok(qrOrderService.getTableSession(tableCode));
    }

    @PostMapping("/order")
    public ApiResponse<QrTableSessionResponse> submitTableOrder(
            @Valid @RequestBody QrOrderSubmitRequest request,
            HttpSession session
    ) {
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        QrTableSessionResponse updated = qrOrderService.submitQrOrder(request, userId);
        return ApiResponse.ok("Order sent to kitchen successfully!", updated);
    }
}
