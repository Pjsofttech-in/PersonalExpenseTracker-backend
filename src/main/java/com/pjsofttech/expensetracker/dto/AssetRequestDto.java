package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.AssetGroup;
import com.pjsofttech.expensetracker.model.AssetType;
import com.pjsofttech.expensetracker.model.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CHANGED: categoryId/contactId (previously required to build a linked
 * Expense) are gone — asset purchases no longer create an Expense.
 * `liabilityId` ADDED to support loan-funded purchases.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetRequestDto {

    @NotBlank
    private String name;
    private Long contactId;

    private AssetGroup assetGroup;
    @NotNull
    private AssetType type;

    private Long assetCategoryId;


    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "Purchase value must be greater than zero.")
    private BigDecimal purchaseValue;

    @DecimalMin(value = "0.0", message = "Current value cannot be negative.")
    private BigDecimal currentValue;

    @NotNull
    private LocalDate purchaseDate;

    @NotNull
    private PaymentMethod paymentMethod;

    /** Required when paymentMethod = BANK_TRANSFER and liabilityId is null. */
    private Long bankId;

    /**
     * Set when this purchase is financed by an existing liability
     * (e.g. asset bought via a loan). Mutually exclusive with a cash/bank
     * debit — the loan already carries the value, this just records that
     * this asset is what the loan bought.
     */
    private Long liabilityId;

    private String remark;
}