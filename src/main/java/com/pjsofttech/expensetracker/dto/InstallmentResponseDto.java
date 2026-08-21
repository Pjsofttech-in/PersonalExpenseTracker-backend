package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.InstallmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentResponseDto {
    private Long id;
    private Integer installmentNumber;
    private BigDecimal dueAmount;
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
    private LocalDate dueDate;
    private InstallmentStatus status;
    private List<InstallmentPaymentResponseDto> payments;
}
