package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishResponse {
    private Integer dishId;
    private Integer categoryId;
    private String categoryName;
    private String dishName;
    private String description;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Integer discountPercent;
    private Integer prepTime;
    private Boolean isSignature;
    private String thumbnailUrl;
}
