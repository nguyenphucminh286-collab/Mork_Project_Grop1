package vn.edu.fpt.savora.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QrOrderSubmitRequest {

    @NotBlank(message = "Table code is required")
    private String tableCode;

    private String voucherCode;

    private String orderNote;

    @NotEmpty(message = "Please select at least 1 dish to order")
    @Valid
    private List<QrCartItemRequest> items;

    @Data
    public static class QrCartItemRequest {
        @NotNull(message = "Dish ID is required")
        private Integer dishId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity = 1;

        private String note;
    }
}
