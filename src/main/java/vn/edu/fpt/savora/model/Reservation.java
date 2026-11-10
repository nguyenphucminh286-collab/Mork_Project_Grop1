package vn.edu.fpt.savora.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.fpt.savora.enums.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "reservations",
        indexes = {
                @Index(name = "idx_reservations_conflict", columnList = "table_id, reservation_date, status, reservation_time, end_time"),
                @Index(name = "idx_reservations_date_time", columnList = "reservation_date, reservation_time"),
                @Index(name = "idx_reservations_customer", columnList = "customer_id, reservation_date"),
                @Index(name = "idx_reservations_pending_exp", columnList = "status, pending_expires_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    public static final int DEFAULT_DURATION_MINUTES = 120;
    public static final int DEFAULT_PENDING_EXPIRATION_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Integer reservationId;

    @Column(name = "reservation_code", nullable = false, unique = true, length = 50)
    private String reservationCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "table_id")
    private DiningTable diningTable;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "reservation_time", nullable = false)
    private LocalTime reservationTime;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = DEFAULT_DURATION_MINUTES;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "guest_count", nullable = false)
    @Builder.Default
    private Integer guestCount = 2;

    @Column(name = "special_request", length = 500)
    private String specialRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "pending_expires_at")
    private LocalDateTime pendingExpiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Tính toán giờ kết thúc dự kiến của lượt đặt bàn
     */
    public LocalTime calculateEndTime() {
        int duration = (this.durationMinutes != null && this.durationMinutes > 0)
                ? this.durationMinutes
                : DEFAULT_DURATION_MINUTES;
        return this.reservationTime != null ? this.reservationTime.plusMinutes(duration) : null;
    }

    /**
     * Kiểm tra 2 khung giờ đặt bàn trong cùng 1 ngày có bị giao nhau (overlap) hay không:
     * [startA, endA) giao với [startB, endB) <=> startA < endB && endA > startB
     */
    public boolean overlapsWith(LocalTime requestedStart, int requestedDurationMinutes) {
        LocalTime existingEnd = this.endTime != null ? this.endTime : calculateEndTime();
        LocalTime requestedEnd = requestedStart.plusMinutes(
                requestedDurationMinutes > 0 ? requestedDurationMinutes : DEFAULT_DURATION_MINUTES
        );
        return this.reservationTime.isBefore(requestedEnd) && existingEnd.isAfter(requestedStart);
    }

    @PrePersist
    protected void onCreate() {
        if (this.durationMinutes == null || this.durationMinutes <= 0) {
            this.durationMinutes = DEFAULT_DURATION_MINUTES;
        }
        this.endTime = calculateEndTime();
        if (this.status == ReservationStatus.PENDING && this.pendingExpiresAt == null) {
            this.pendingExpiresAt = LocalDateTime.now().plusMinutes(DEFAULT_PENDING_EXPIRATION_MINUTES);
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.durationMinutes == null || this.durationMinutes <= 0) {
            this.durationMinutes = DEFAULT_DURATION_MINUTES;
        }
        this.endTime = calculateEndTime();
        this.updatedAt = LocalDateTime.now();
    }
}
