package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BankResponseDto {
    private Long id;
    private String name;
    private String branch;
    private String accountNumber;
    private String ifsc;
    private AccountType accountType;
    private BigDecimal openingBalance;

    private BigDecimal currentBalance;
}
