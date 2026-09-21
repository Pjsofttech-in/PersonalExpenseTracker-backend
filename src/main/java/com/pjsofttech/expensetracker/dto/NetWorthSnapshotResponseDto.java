package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class NetWorthSnapshotResponseDto {

    private Long id;
    private Integer year;
    private LocalDate snapshotDate;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
    private LocalDateTime createdAt;
}
