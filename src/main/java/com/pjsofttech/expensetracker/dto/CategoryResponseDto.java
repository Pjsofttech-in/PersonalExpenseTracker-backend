package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryResponseDto {
    private Long id;
    private String name;
    private TransactionType transactionType;
}
