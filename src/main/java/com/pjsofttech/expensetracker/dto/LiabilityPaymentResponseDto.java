package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiabilityPaymentResponseDto {

    private Long id;
    private LocalDate paymentDate;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private Long bankId;
    private Long interestExpenseId;
    private String remark;
}