package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.LiabilityGroup;
import com.pjsofttech.expensetracker.model.LiabilityType;
import com.pjsofttech.expensetracker.model.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * NOTE: there is deliberately no `outstandingAmount` field here.
 * Outstanding amount is always derived/managed server-side.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiabilityRequestDto {

    @NotBlank
    private String name;
    private LiabilityGroup liabilityGroup;

    @NotNull
    private LiabilityType type;

    private Long lenderContactId;

    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "Original amount must be greater than zero.")
    private BigDecimal originalAmount;

    @DecimalMin(value = "0.0", message = "Interest rate cannot be negative.")
    private BigDecimal interestRate;

    @NotNull
    private LocalDate startDate;

    private LocalDate dueDate;

    /**
     * If the loan proceeds are being deposited into a bank at creation time,
     * set this. Leave null if the loan wasn't deposited anywhere tracked
     * (e.g. it directly funds an asset purchase, or is informal/cash).
     */
    private Long depositBankId;

    /** Amount actually deposited to depositBankId. Defaults to principalAmount if bank is set and this is null. */
    private BigDecimal depositAmount;
}