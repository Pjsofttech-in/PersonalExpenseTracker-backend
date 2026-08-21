package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
