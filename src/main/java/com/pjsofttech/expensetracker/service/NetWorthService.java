package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.*;
import com.pjsofttech.expensetracker.repository.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles:
 *   - Current net worth calculation (read-only; never touches bank balances)
 *   - Net worth target create / read / delete
 *   - Net worth snapshot create (manual trigger or service-internal)
 *   - Net worth snapshot read
 *
 * All queries are scoped to the authenticated user; owner is never taken
 * from the request body.
 */
@Service
@RequiredArgsConstructor
public class NetWorthService {

    private final AssetRepository           assetRepository;
    private final LiabilityRepository       liabilityRepository;
    private final NetWorthTargetRepository  targetRepository;
    private final NetWorthSnapshotRepository snapshotRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // CURRENT NET WORTH (read-only)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculates the user's current net worth from live asset and liability data.
     *
     * Uses aggregate repository queries (SUM) to avoid loading all entities
     * into memory.  Both queries return 0 (not null) via COALESCE.
     *
     * IMPORTANT: this method is read-only; it must never modify any entity.
     */
    public NetWorthResponseDto getCurrentNetWorth(User loggedInUser) {
        BigDecimal totalAssets      = assetRepository.getTotalCurrentValueByOwner(loggedInUser);
        BigDecimal totalLiabilities = liabilityRepository.getTotalOutstandingByOwner(loggedInUser);

        // Null-safe fallbacks in case the COALESCE in the query somehow fails
        if (totalAssets == null)      totalAssets      = BigDecimal.ZERO;
        if (totalLiabilities == null) totalLiabilities = BigDecimal.ZERO;

        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);

        return NetWorthResponseDto.builder()
                .totalAssets(totalAssets.setScale(2, RoundingMode.HALF_UP))
                .totalLiabilities(totalLiabilities.setScale(2, RoundingMode.HALF_UP))
                .netWorth(netWorth.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TARGET — CREATE / UPDATE (upsert via PUT)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates or replaces the user's active net-worth target.
     *
     * Business rules:
     *   - targetYear must be >= the current calendar year.
     *   - inflationRate must be >= 0.
     *   - targetAmount must be > 0 (enforced by @DecimalMin on the DTO).
     *   - Any previously active target is deactivated first; only one active
     *     target exists per user at any time.
     *
     * targetAmount is stored as-is (nominal future amount).
     * It is NOT automatically inflated from today's purchasing power.
     */
    @Transactional
    public NetWorthTargetResponseDto upsertTarget(@Valid NetWorthTargetRequestDto req,
                                                  User loggedInUser) {
        int currentYear = LocalDate.now().getYear();

        if (req.getTargetYear() < currentYear) {
            throw new IllegalArgumentException(
                    "targetYear must be >= the current year (" + currentYear + ").");
        }
        if (req.getInflationRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("inflationRate cannot be negative.");
        }

        // Deactivate any existing active target
        targetRepository.deactivateAllForOwner(loggedInUser);

        NetWorthTarget target = NetWorthTarget.builder()
                .owner(loggedInUser)
                .targetAmount(req.getTargetAmount().setScale(2, RoundingMode.HALF_UP))
                .targetYear(req.getTargetYear())
                .inflationRate(req.getInflationRate().setScale(2, RoundingMode.HALF_UP))
                .active(true)
                .build();

        return mapTargetToResponse(targetRepository.save(target));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TARGET — READ
    // ═══════════════════════════════════════════════════════════════════════

    public NetWorthTargetResponseDto getActiveTarget(User loggedInUser) {
        NetWorthTarget target = targetRepository.findByOwnerAndActiveTrue(loggedInUser)
                .orElseThrow(() -> new RuntimeException("No active net-worth target found."));
        return mapTargetToResponse(target);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TARGET — DELETE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public String deleteActiveTarget(User loggedInUser) {
        NetWorthTarget target = targetRepository.findByOwnerAndActiveTrue(loggedInUser)
                .orElseThrow(() -> new RuntimeException("No active net-worth target found."));
        targetRepository.delete(target);
        return "Net-worth target deleted successfully.";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SNAPSHOT — CREATE (manual trigger or called by scheduler)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Takes a point-in-time snapshot of the user's net worth for the current
     * calendar year.
     *
     * If a snapshot already exists for this (owner, year) pair it is updated
     * (upsert), so the most recent figures are always stored.
     *
     * NOTE: This method does NOT change any bank balance or financial record.
     * It is read-only except for writing to the net_worth_snapshots table.
     */


    //Run the method every year on December 31st at 11:00 PM.
 //|──────── second
 //│  ┌────── minute
 //│  │  ┌──── hour
 //│  │  │  ┌── day of month
 //│  │  │  │  ┌ month
 //│  │  │  │  │  ┌ day of week
 //│  │  │  │  │  │
// 0  0  23 31 12 *
//    @Scheduled(cron = "0 0 23 31 12 *")
    @Transactional
    public NetWorthSnapshotResponseDto takeSnapshot(User loggedInUser) {
        NetWorthResponseDto current = getCurrentNetWorth(loggedInUser);
        int year = LocalDate.now().getYear();


        NetWorthSnapshot snapshot = NetWorthSnapshot.builder()
                .owner(loggedInUser)
                .year(LocalDate.now().getYear())
                .snapshotDate(LocalDate.now())
                .totalAssets(current.getTotalAssets())
                .totalLiabilities(current.getTotalLiabilities())
                .netWorth(current.getNetWorth())
                .build();

        return mapSnapshotToResponse(snapshotRepository.save(snapshot));

        //This commented code is useful for Yearly snapshots means only 1 snapshot per year
//        Optional<NetWorthSnapshot> existing = snapshotRepository.findByOwnerAndYear(loggedInUser, year);
//
//        NetWorthSnapshot snapshot = existing.orElseGet(() ->
//                NetWorthSnapshot.builder()
//                        .owner(loggedInUser)
//                        .year(year)
//                        .build());
//
//        snapshot.setSnapshotDate(LocalDate.now());
//        snapshot.setTotalAssets(current.getTotalAssets());
//        snapshot.setTotalLiabilities(current.getTotalLiabilities());
//        snapshot.setNetWorth(current.getNetWorth());
//
//        return mapSnapshotToResponse(snapshotRepository.save(snapshot));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SNAPSHOT — READ
    // ═══════════════════════════════════════════════════════════════════════

    public List<NetWorthSnapshotResponseDto> getAllSnapshots(User loggedInUser) {
        return snapshotRepository.findByOwnerOrderByYearAsc(loggedInUser)
                .stream()
                .map(this::mapSnapshotToResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS — also used by NetWorthProjectionService
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the raw current net worth as a BigDecimal.
     * Used by the projection service to avoid rebuilding the DTO.
     */
    public BigDecimal getCurrentNetWorthValue(User loggedInUser) {
        BigDecimal totalAssets = assetRepository.getTotalCurrentValueByOwner(loggedInUser);
        BigDecimal totalLiabilities = liabilityRepository.getTotalOutstandingByOwner(loggedInUser);
        if (totalAssets == null)      totalAssets      = BigDecimal.ZERO;
        if (totalLiabilities == null) totalLiabilities = BigDecimal.ZERO;
        return totalAssets.subtract(totalLiabilities).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalAssetsValue(User loggedInUser) {
        BigDecimal v = assetRepository.getTotalCurrentValueByOwner(loggedInUser);
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalLiabilitiesValue(User loggedInUser) {
        BigDecimal v = liabilityRepository.getTotalOutstandingByOwner(loggedInUser);
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAPPERS
    // ═══════════════════════════════════════════════════════════════════════

    private NetWorthTargetResponseDto mapTargetToResponse(NetWorthTarget t) {
        return NetWorthTargetResponseDto.builder()
                .id(t.getId())
                .targetAmount(t.getTargetAmount())
                .targetYear(t.getTargetYear())
                .inflationRate(t.getInflationRate())
                .active(t.getActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private NetWorthSnapshotResponseDto mapSnapshotToResponse(NetWorthSnapshot s) {
        return NetWorthSnapshotResponseDto.builder()
                .id(s.getId())
                .year(s.getYear())
                .snapshotDate(s.getSnapshotDate())
                .totalAssets(s.getTotalAssets())
                .totalLiabilities(s.getTotalLiabilities())
                .netWorth(s.getNetWorth())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
