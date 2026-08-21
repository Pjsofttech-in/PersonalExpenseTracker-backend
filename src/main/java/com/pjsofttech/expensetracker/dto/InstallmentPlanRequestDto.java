package com.pjsofttech.expensetracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class InstallmentPlanRequestDto {

    @NotNull
    @Positive
    private Integer numberOfInstallments;

    @NotNull
    private List<InstallmentScheduleRequestDto> installments;

}
