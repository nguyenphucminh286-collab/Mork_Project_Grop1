package vn.edu.fpt.savora.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.fpt.savora.enums.TableStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "dining_tables",
        indexes = {
                @Index(name = "idx_tables_status_capacity", columnList = "status, capacity"),
                @Index(name = "idx_tables_area", columnList = "area")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "table_id")
    private Integer tableId;

    @Column(name = "table_code", nullable = false, unique = true, length = 30)
    private String tableCode;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "area", nullable = false, length = 100)
    private String area;

    @Column(name = "capacity", nullable = false)
    @Builder.Default
    private Integer capacity = 4;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TableStatus status = TableStatus.AVAILABLE;

    @Column(name = "note")
    private String note;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
