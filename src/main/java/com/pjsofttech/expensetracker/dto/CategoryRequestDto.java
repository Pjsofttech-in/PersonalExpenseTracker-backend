package com.pjsofttech.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequestDto {
    @NotBlank(message = "Category is required")
    private String name;
}
