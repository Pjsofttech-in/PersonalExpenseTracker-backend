package com.pjsofttech.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentPaymentResponseDto {
    private Long id;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String remark;
}