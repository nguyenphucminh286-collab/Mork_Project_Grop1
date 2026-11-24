package vn.edu.fpt.savora.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class PasGoReservationRequest {

    @NotNull(message = "Please select the number of adults")
    @Min(value = 1, message = "At least 1 adult is required")
    private Integer adultCount = 2;

    @NotNull(message = "Please select the number of children")
    @Min(value = 0, message = "Children count cannot be negative")
    private Integer childCount = 0;

    @NotNull(message = "Please select your arrival date")
    private LocalDate reservationDate;

    @NotNull(message = "Please select your arrival time slot")
    private LocalTime reservationTime;

    private String preferredArea;

    @NotNull(message = "Please select an available dining table")
    private Integer tableId;

    private Integer voucherId;

    private String occasion;

    @NotBlank(message = "Please enter the contact full name")
    private String customerName;

    @NotBlank(message = "Please enter the contact phone number")
    private String customerPhone;

    private String customerEmail;

    private String specialRequest;
}
