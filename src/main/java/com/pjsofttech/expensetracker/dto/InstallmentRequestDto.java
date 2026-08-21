package com.pjsofttech.expensetracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class InstallmentRequestDto {

    @NotNull
    @Positive
    private Long amount;

    @NotNull
    private LocalDate date;

//    private String remark;
}
