package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class NetWorthTargetResponseDto {

    private Long id;
    private BigDecimal targetAmount;
    private Integer targetYear;
    private BigDecimal inflationRate;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
