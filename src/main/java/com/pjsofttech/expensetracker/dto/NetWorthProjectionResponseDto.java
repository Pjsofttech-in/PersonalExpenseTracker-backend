package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** Full projection response returned by GET /api/net-worth/projection */
@Getter
@Builder
public class NetWorthProjectionResponseDto {

    private Summary summary;
    private Assumptions assumptions;
    private List<NetWorthProjectionPointDto> yearlyData;

    @Getter
    @Builder
    public static class Summary {
        private BigDecimal currentNetWorth;
        private BigDecimal totalAssets;
        private BigDecimal totalLiabilities;
        private BigDecimal targetAmount;
        private Integer targetYear;
        private Integer yearsRemaining;
        /** (currentNetWorth / targetAmount) × 100, capped at 100. */
        private BigDecimal progressPercentage;
        /** (targetAmount - currentNetWorth) / yearsRemaining */
        private BigDecimal requiredAnnualSaving;
    }

    @Getter
    @Builder
    public static class Assumptions {
        private BigDecimal inflationRate;
        /**
         * Describes how projectedNetWorth was derived:
         *   "HISTORICAL_AVERAGE" — calculated from snapshot history
         *   "REQUIRED_SAVINGS"   — insufficient history; used target path as baseline
         */
        private String projectionMethod;
        /** Average annual net-worth change used for projection (may be null). */
        private BigDecimal averageAnnualGrowth;
        /** Annual expense used as inflation base (may be null if no expense data). */
        private BigDecimal baseAnnualExpense;
    }
}
