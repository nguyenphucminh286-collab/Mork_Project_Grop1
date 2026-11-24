package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChefKitchenItemResponse {
    private Integer orderItemId;
    private Integer orderId;
    private String orderCode;
    private String tableCode;
    private String tableName;
    private String area;
    private Integer dishId;
    private String dishName;
    private String categoryName;
    private String thumbnailUrl;
    private Integer prepTimeMinutes;
    private Integer quantity;
    private String note;
    private String orderNote;
    private String itemStatus;
    private String orderedAt;
}
