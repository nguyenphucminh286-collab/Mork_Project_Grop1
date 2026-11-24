package vn.edu.fpt.savora.controller.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.savora.dto.response.QrTableSessionResponse;
import vn.edu.fpt.savora.service.QrOrderService;

@Controller
@RequiredArgsConstructor
public class QrOrderPageController {

    private final QrOrderService qrOrderService;

    @GetMapping("/qr/table/{tableCode}")
    public String qrTablePage(@PathVariable String tableCode, Model model) {
        populateTableModel(tableCode, model);
        return "public/qr/qr-order";
    }

    @GetMapping("/qr/order")
    public String qrOrderPage(@RequestParam(name = "table", defaultValue = "T1-B01") String tableCode, Model model) {
        populateTableModel(tableCode, model);
        return "public/qr/qr-order";
    }

    private void populateTableModel(String tableCode, Model model) {
        String code = tableCode != null ? tableCode.trim().toUpperCase() : "T1-B01";
        model.addAttribute("initialTableCode", code);
        try {
            QrTableSessionResponse session = qrOrderService.getTableSession(code);
            model.addAttribute("tableSession", session);
            model.addAttribute("initialTableCode", session.getTableCode());
        } catch (Exception ignored) {
        }
    }
}
