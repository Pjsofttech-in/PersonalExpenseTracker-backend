package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.*;
import com.pjsofttech.expensetracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the yearly projection data consumed by the frontend graph.
 *
 * ════════════════════════════════════════════════════════════
 * THREE LINES ON THE GRAPH
 * ════════════════════════════════════════════════════════════
 *
 * 1. actualNetWorth
 *    — Real recorded net worth.
 *    — Past years: from NetWorthSnapshot (if the user has taken one).
 *    — Current year: dynamically calculated from live assets/liabilities.
 *    — Future years: null (no data yet).
 *
 * 2. targetNetWorth   (linear interpolation between currentNetWorth → targetAmount)
 *    — The yearly net-worth level the user MUST reach to hit their target.
 *    — First year = currentNetWorth, final year = targetAmount.
 *    — Formula: currentNetWorth + (requiredAnnualSaving × yearsElapsed)
 *
 * 3. projectedNetWorth   (simple forecast)
 *    — IF ≥ 2 annual snapshots exist: use average annual net-worth change.
 *    — IF insufficient history: fall back to the target path (requiredAnnualSaving).
 *    — This is labeled in the "assumptions.projectionMethod" field.
 *
 * ════════════════════════════════════════════════════════════
 * EXPENSE INFLATION (informational)
 * ════════════════════════════════════════════════════════════
 *   baseAnnualExpense × (1 + inflationRate/100)^years
 *   Stored in assumptions.baseAnnualExpense for reference.
 *   NOT subtracted from projectedNetWorth in this implementation
 *   (future enhancement: deduct projected expenses from projected savings).
 *
 * ════════════════════════════════════════════════════════════
 * IMPORTANT: This service is read-only.
 * It must NEVER modify Bank, Asset, Liability, or Expense records.
 * ════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class NetWorthProjectionService {

    private static final int    SNAPSHOT_HISTORY_FOR_AVERAGE = 5;
    private static final int    SCALE                        = 2;
    private static final RoundingMode ROUNDING               = RoundingMode.HALF_UP;

    private final NetWorthService            netWorthService;
    private final NetWorthTargetRepository   targetRepository;
    private final NetWorthSnapshotRepository snapshotRepository;
    private final ExpenseRepository          expenseRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════

    public NetWorthProjectionResponseDto buildProjection(User loggedInUser) {

        // ── 1. Require an active target ──────────────────────────────────────
        NetWorthTarget target = targetRepository.findByOwnerAndActiveTrue(loggedInUser)
                .orElseThrow(() -> new RuntimeException(
                        "No active net-worth target found. " +
                        "Please create a target via PUT /api/net-worth/target before requesting a projection."));

        int currentYear  = LocalDate.now().getYear();
        int targetYear   = target.getTargetYear();
        BigDecimal targetAmount = target.getTargetAmount();
        BigDecimal inflationRate = target.getInflationRate();



        // ── 2. Current net worth ─────────────────────────────────────────────
        BigDecimal currentNetWorth   = netWorthService.getCurrentNetWorthValue(loggedInUser);
        BigDecimal totalAssets       = netWorthService.getTotalAssetsValue(loggedInUser);
        BigDecimal totalLiabilities  = netWorthService.getTotalLiabilitiesValue(loggedInUser);

        // ── 3. Required annual saving (simple linear) ────────────────────────
        int yearsRemaining = Math.max(targetYear - currentYear, 1);
        BigDecimal requiredAnnualSaving = targetAmount
                .subtract(currentNetWorth)
                .divide(BigDecimal.valueOf(yearsRemaining), SCALE, ROUNDING);

        // ── 4. Load historical snapshots keyed by year ───────────────────────
        Map<Integer, BigDecimal> snapshotByYear = snapshotRepository
                .findByOwnerOrderByYearAsc(loggedInUser)
                .stream()
                .collect(Collectors.toMap(
                        NetWorthSnapshot::getYear,
                        NetWorthSnapshot::getNetWorth,
                        (oldValue, newValue) -> newValue
                ));

        // ── 5. Determine projection method ───────────────────────────────────
        //       Need at least 2 snapshots to compute a meaningful average.
        BigDecimal averageAnnualGrowth = computeAverageAnnualGrowth(loggedInUser);
        String projectionMethod = (averageAnnualGrowth != null)
                ? "HISTORICAL_AVERAGE"
                : "INCOME_EXPENSE_INFLATION";

        // ── 6. Base annual income and expense ───────────────────────────────
// Previous calendar year's actual income/expense are used as the
// starting point for future projection.

        BigDecimal baseAnnualIncome = getLastCalendarYearIncome(
                loggedInUser, currentYear);

        BigDecimal baseAnnualExpense = getLastCalendarYearExpense(
                loggedInUser, currentYear);

        // ── 7. Progress ──────────────────────────────────────────────────────
        BigDecimal progressPercentage;
        if (targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            progressPercentage = BigDecimal.valueOf(100);
        } else {
            progressPercentage = currentNetWorth
                    .multiply(BigDecimal.valueOf(100))
                    .divide(targetAmount, SCALE, ROUNDING);
            // Cap at 100 for display purposes
            if (progressPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                progressPercentage = BigDecimal.valueOf(100);
            }
        }

        // ── 8. Build yearly data points ──────────────────────────────────────
        List<NetWorthProjectionPointDto> yearlyData = new ArrayList<>();

        for (int year = currentYear; year <= targetYear; year++) {
            int yearsElapsed = year - currentYear;

            // actualNetWorth
            BigDecimal actual;
            if (year == currentYear) {
                actual = currentNetWorth; // live calculation
            } else {
                actual = snapshotByYear.getOrDefault(year, null); // null if no snapshot
            }

            // targetNetWorth (linear path)
            BigDecimal targetForYear;
            if (year == targetYear) {
                targetForYear = targetAmount; // ensure exact match at the end
            } else {
                targetForYear = currentNetWorth
                        .add(requiredAnnualSaving.multiply(BigDecimal.valueOf(yearsElapsed)))
                        .setScale(SCALE, ROUNDING);
            }

            // projectedNetWorth
//            BigDecimal projected;
//            if (year == currentYear) {
//                projected = currentNetWorth;
//            } else if (averageAnnualGrowth != null) {
//                // Use historical average: project from current net worth
//                projected = currentNetWorth
//                        .add(averageAnnualGrowth.multiply(BigDecimal.valueOf(yearsElapsed)))
//                        .setScale(SCALE, ROUNDING);
//            } else {
//                 BigDecimal DEFAULT_ANNUAL_GROWTH_RATE =
//                        BigDecimal.valueOf(8.0);
//                BigDecimal growthRate = DEFAULT_ANNUAL_GROWTH_RATE
//                        .divide(BigDecimal.valueOf(100), 10, ROUNDING);
//
//                projected = currentNetWorth
//                        .multiply(
//                                BigDecimal.ONE
//                                        .add(growthRate)
//                                        .pow(yearsElapsed)
//                        )
//                        .setScale(SCALE, ROUNDING);
//                // Insufficient history: mirror the target path as the projection
////                projected = targetForYear;
//            }



            // ── Projected Net Worth ─────────────────────────────────────────────
//
// Formula:
//
// Projected Expense
//     = Base Annual Expense × (1 + Inflation)^yearsElapsed
//
// Annual Savings
//     = Annual Income - Projected Expense
//
// Projected Net Worth
//     = Previous Year's Projected Net Worth + Annual Savings
//
// Example:
// Current Net Worth = ₹5,00,000
// Income           = ₹6,00,000
// Expense          = ₹2,40,000
// Inflation        = 6%
//
// 2027 Expense = 2,40,000 × 1.06 = ₹2,54,400
// 2027 Savings = 6,00,000 - 2,54,400 = ₹3,45,600
// 2027 Net Worth = 5,00,000 + 3,45,600 = ₹8,45,600

            BigDecimal projected;

            if (year == currentYear) {

                // Current year starts from the user's real current net worth.
                projected = currentNetWorth;

            } else {

                // Convert inflation percentage into decimal.
                // Example: 6% -> 0.06
                BigDecimal inflation =
                        inflationRate.divide(
                                BigDecimal.valueOf(100),
                                10,
                                ROUNDING
                        );

                // Calculate expense for this future year.
                //
                // Formula:
                // Base Expense × (1 + inflation)^yearsElapsed
            BigDecimal projectedBaseExpense =
                    baseAnnualExpense != null
                            ? baseAnnualExpense
                            : BigDecimal.ZERO;
                BigDecimal projectedExpense = projectedBaseExpense
                        .multiply(
                                BigDecimal.ONE
                                        .add(inflation)
                                        .pow(yearsElapsed)
                        )
                        .setScale(SCALE, ROUNDING);

                // Income is currently kept constant.
                // Example: ₹6,00,000 every year.
                BigDecimal projectedIncome = baseAnnualIncome;

                // Money available to increase net worth.
                //
                // Formula:
                // Income - Expense
                BigDecimal annualSavings = projectedIncome
                        .subtract(projectedExpense)
                        .setScale(SCALE, ROUNDING);

                // Add this year's savings to the previous projected net worth.
                //
                // Example:
                // Previous projection = ₹5,00,000
                // Annual savings      = ₹3,45,600
                // New projection      = ₹8,45,600
                projected = yearlyData.isEmpty()
                        ? currentNetWorth.add(annualSavings)
                        : yearlyData.get(yearlyData.size() - 1)
                        .getProjectedNetWorth()
                        .add(annualSavings)
                        .setScale(SCALE, ROUNDING);
            }






            yearlyData.add(NetWorthProjectionPointDto.builder()
                    .year(year)
                    .actualNetWorth(actual != null ? actual.setScale(SCALE, ROUNDING) : null)
                    .projectedNetWorth(projected)
                    .targetNetWorth(targetForYear)
                    .requiredAnnualSaving(requiredAnnualSaving)
                    .build());
        }

        // ── 9. Assemble response ─────────────────────────────────────────────
        return NetWorthProjectionResponseDto.builder()
                .summary(NetWorthProjectionResponseDto.Summary.builder()
                        .currentNetWorth(currentNetWorth)
                        .totalAssets(totalAssets)
                        .totalLiabilities(totalLiabilities)
                        .targetAmount(targetAmount)
                        .targetYear(targetYear)
                        .yearsRemaining(yearsRemaining)
                        .progressPercentage(progressPercentage)
                        .requiredAnnualSaving(requiredAnnualSaving)
                        .build())
                .assumptions(NetWorthProjectionResponseDto.Assumptions.builder()
                        .inflationRate(inflationRate)
                        .projectionMethod(projectionMethod)
                        .averageAnnualGrowth(averageAnnualGrowth)
                        .baseAnnualIncome(baseAnnualIncome)
                        .baseAnnualExpense(baseAnnualExpense)
                        .build())
                .yearlyData(yearlyData)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculates the average annual net-worth change from recent snapshots.
     *
     * Algorithm:
     *   Load up to SNAPSHOT_HISTORY_FOR_AVERAGE snapshots (newest first).
     *   Need at least 2 to compute a delta.
     *   Average the year-over-year changes.
     *
     * Returns null if there is insufficient snapshot history.
     */
    private BigDecimal computeAverageAnnualGrowth(User loggedInUser) {
        List<NetWorthSnapshot> recent = snapshotRepository.findRecentByOwner(
                loggedInUser, SNAPSHOT_HISTORY_FOR_AVERAGE);

        if (recent == null || recent.size() < 2) {
            return null;
        }

        // Sort ascending so we can diff consecutive years
        recent.sort(Comparator.comparingInt(NetWorthSnapshot::getYear));

        BigDecimal totalChange = BigDecimal.ZERO;
        int count = 0;

        for (int i = 1; i < recent.size(); i++) {
            BigDecimal change = recent.get(i).getNetWorth()
                    .subtract(recent.get(i - 1).getNetWorth());
            totalChange = totalChange.add(change);
            count++;
        }

        if (count == 0) return null;

        return totalChange.divide(BigDecimal.valueOf(count), SCALE, ROUNDING);
    }

    /**
     * Returns the total EXPENSE-type spending for the previous calendar year.
     * Used as the inflation base amount in Assumptions.
     *
     * If no expense data exists (user just registered, or the query returns 0)
     * we return null rather than 0 to signal "no expense history available".
     */
    private BigDecimal getLastCalendarYearExpense(User loggedInUser, int currentYear) {
        int prevYear = currentYear - 1;
        LocalDateTime from = LocalDateTime.of(prevYear, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(currentYear, 1, 1, 0, 0);

        BigDecimal total = expenseRepository.getTotalExpenseByOwnerAndDateRange(
                loggedInUser, from, to);

        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return total.setScale(SCALE, ROUNDING);
    }

    //calculate the previous year's income from ExpenseRepository.
    private BigDecimal getLastCalendarYearIncome(User loggedInUser, int currentYear) {
        int prevYear = currentYear - 1;

        LocalDateTime from = LocalDateTime.of(prevYear, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(currentYear, 1, 1, 0, 0);

        BigDecimal total = expenseRepository
                .getTotalIncomeByOwnerAndDateRange(loggedInUser, from, to);

        return total == null
                ? BigDecimal.ZERO
                : total.setScale(SCALE, ROUNDING);
    }
}
