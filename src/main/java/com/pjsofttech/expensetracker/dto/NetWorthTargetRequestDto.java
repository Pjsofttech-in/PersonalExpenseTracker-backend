package com.pjsofttech.expensetracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class NetWorthTargetRequestDto {

    /**
     * Nominal net-worth amount the user wants to reach by targetYear.
     * Must be > 0. This is NOT adjusted for inflation by the backend.
     */
    @NotNull(message = "targetAmount is required")
    @DecimalMin(value = "0.01", message = "targetAmount must be greater than zero")
    private BigDecimal targetAmount;

    /**
     * Calendar year in which the user wants to reach targetAmount.
     * Must be >= the current year. Validated in the service layer
     * because @Min on Integer doesn't capture the dynamic current year.
     */
    @NotNull(message = "targetYear is required")
    private Integer targetYear;

    /**
     * Annual inflation rate as a percentage (e.g. 6.0 for 6 %).
     * Must be >= 0. Used for expense projection; does NOT inflate targetAmount.
     */
    @NotNull(message = "inflationRate is required")
    @DecimalMin(value = "0", message = "inflationRate cannot be negative")
    private BigDecimal inflationRate;
}
