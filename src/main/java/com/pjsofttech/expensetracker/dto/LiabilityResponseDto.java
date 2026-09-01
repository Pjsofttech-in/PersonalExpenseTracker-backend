package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.LiabilityStatus;
import com.pjsofttech.expensetracker.model.LiabilityType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiabilityResponseDto {

    private Long id;
    private String name;
    private LiabilityType type;
    private Long lenderContactId;
    private String lenderName;
    private String description;

    private BigDecimal principalAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal totalPrincipalPaid;
    private BigDecimal totalInterestPaid;
    private BigDecimal interestRate;

    private LocalDate startDate;
    private LocalDate dueDate;

    private Long bankId;
    private String bankName;

    private LiabilityStatus status;

    private List<LiabilityPaymentResponseDto> payments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}