package com.pjsofttech.expensetracker.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents one row of the installment SCHEDULE sent when creating an expense.
 * This is NOT a payment — it is a planned future due amount.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentScheduleDto {

    @NotNull(message = "Installment number is required")
    @Min(value = 1, message = "Installment number must be at least 1")
    private Integer installmentNumber;

    @NotNull(message = "Due amount is required")
    @DecimalMin(value = "0.01", message = "Due amount must be greater than zero")
    private BigDecimal dueAmount;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;
}
