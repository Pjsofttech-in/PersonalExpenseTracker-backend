package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Snapshot of a user's current net worth. */
@Getter
@Builder
public class NetWorthResponseDto {

    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
}
