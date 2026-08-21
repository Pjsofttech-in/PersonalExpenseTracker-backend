package com.pjsofttech.expensetracker.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InstallmentScheduleRequestDto {

    private Integer installmentNumber;

    private Long dueAmount;

    private LocalDate dueDate;
}
