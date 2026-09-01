package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BankRequestDto {
    private String name;
    private String branch;
    private String accountNumber;
    private String ifsc;
    private AccountType accountType;
    @NotNull(message = "Opening balance is required")
    @DecimalMin(
            value = "0.00",
            message = "Opening balance cannot be negative"
    )
    private BigDecimal openingBalance;
}
