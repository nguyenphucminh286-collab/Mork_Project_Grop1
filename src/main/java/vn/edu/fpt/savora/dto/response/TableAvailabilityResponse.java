package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableAvailabilityResponse {
    private Integer tableId;
    private String tableCode;
    private String tableName;
    private String area;
    private Integer capacity;
    private String operationalStatus;
    private boolean bookableForSlot;
    private String unavailableReason;
    private String note;
}
