package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequestDto {
    @NotBlank(message = "Category is required")
    private String name;
    private TransactionType transactionType;
}
