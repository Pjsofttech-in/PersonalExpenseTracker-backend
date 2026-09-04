package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiabilityPaymentRequestDto {

    @NotNull
    private LocalDate date;
    /**
     * Used for BILL / FEE / CREDIT_CARD / OTHER.
     */
    @DecimalMin("0.01")
    private BigDecimal paymentAmount;

    @DecimalMin(value = "0.0")
    private BigDecimal principalComponent;


    @DecimalMin(value = "0.0")
    private BigDecimal interestComponent;

    @NotNull
    private PaymentMethod paymentMethod;

    /** Required when paymentMethod = BANK_TRANSFER. */
    private Long bankId;

    /** Category to file the interest-portion Expense under (e.g. "Interest"). Required only if interestComponent > 0. */
    private Long interestCategoryId;

    private String remark;
}