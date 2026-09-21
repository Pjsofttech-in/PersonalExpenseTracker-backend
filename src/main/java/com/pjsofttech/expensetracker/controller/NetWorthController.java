package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.service.NetWorthProjectionService;
import com.pjsofttech.expensetracker.service.NetWorthService;
import com.pjsofttech.expensetracker.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for all net-worth endpoints.
 *
 * Endpoints:
 *   GET    /api/net-worth              — current net worth
 *   GET    /api/net-worth/target       — active target
 *   PUT    /api/net-worth/target       — create/replace active target
 *   DELETE /api/net-worth/target       — delete active target
 *   GET    /api/net-worth/projection   — full projection with graph data
 *   POST   /api/net-worth/snapshot     — take a manual snapshot
 *   GET    /api/net-worth/snapshots    — list all snapshots
 *
 * Authentication: uses the same pattern as the existing controllers in this
 * project (Authentication → UserService.findByEmail → loggedInUser).
 * Never accept an owner ID from the request body.
 */
@RestController
@RequestMapping("/api/net-worth")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NetWorthController {

    private final NetWorthService           netWorthService;
    private final NetWorthProjectionService projectionService;
    private final UserService               userService;

    // ── resolve the authenticated user the same way existing controllers do ──
    private User loggedInUser(Authentication auth) {
        return userService.findByEmail(auth.getName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CURRENT NET WORTH
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/net-worth
     *
     * Returns the user's current net worth calculated live from assets and
     * liabilities. This call is read-only.
     *
     * Example response:
     * {
     *   "totalAssets": 1250000.00,
     *   "totalLiabilities": 850000.00,
     *   "netWorth": 400000.00
     * }
     */
    @GetMapping
    public ResponseEntity<NetWorthResponseDto> getCurrentNetWorth(Authentication auth) {
        return ResponseEntity.ok(netWorthService.getCurrentNetWorth(loggedInUser(auth)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TARGET
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/net-worth/target
     *
     * Returns the user's active net-worth target, or 404 if none exists.
     */
    @GetMapping("/target")
    public ResponseEntity<NetWorthTargetResponseDto> getTarget(Authentication auth) {
        return ResponseEntity.ok(netWorthService.getActiveTarget(loggedInUser(auth)));
    }

    /**
     * PUT /api/net-worth/target
     *
     * Creates or replaces the user's active net-worth target.
     * Any previously active target is deactivated first.
     *
     * Request body:
     * {
     *   "targetAmount": 1000000,
     *   "targetYear": 2045,
     *   "inflationRate": 6.0
     * }
     *
     * Validation:
     *   - targetAmount > 0
     *   - targetYear >= current year
     *   - inflationRate >= 0
     */
    @PutMapping("/target")
    public ResponseEntity<NetWorthTargetResponseDto> upsertTarget(
            @Valid @RequestBody NetWorthTargetRequestDto req,
            Authentication auth) {
        return ResponseEntity.ok(netWorthService.upsertTarget(req, loggedInUser(auth)));
    }

    /**
     * DELETE /api/net-worth/target
     *
     * Permanently deletes the user's active net-worth target.
     */
    @DeleteMapping("/target")
    public ResponseEntity<String> deleteTarget(Authentication auth) {
        return ResponseEntity.ok(netWorthService.deleteActiveTarget(loggedInUser(auth)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROJECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/net-worth/projection
     *
     * Returns year-by-year projection data from the current year through
     * the target year, ready for direct use by a frontend chart.
     *
     * Requires an active net-worth target. Returns 400 if none is set.
     *
     * Graph lines returned:
     *   actualNetWorth    — real recorded net worth (null for future years)
     *   projectedNetWorth — forecast based on historical growth or target path
     *   targetNetWorth    — the linear path needed to reach the target
     */
    @GetMapping("/projection")
    public ResponseEntity<NetWorthProjectionResponseDto> getProjection(Authentication auth) {
        return ResponseEntity.ok(projectionService.buildProjection(loggedInUser(auth)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SNAPSHOTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * POST /api/net-worth/snapshot
     *
     * Takes a point-in-time snapshot of the user's current net worth for the
     * current calendar year. If a snapshot already exists for this year it is
     * updated (upsert).
     *
     * Call this periodically (e.g. at year end) to build up historical data
     * that populates the "actual" line on the graph for past years.
     */
    @PostMapping("/snapshot")
    public ResponseEntity<NetWorthSnapshotResponseDto> takeSnapshot(Authentication auth) {
        return ResponseEntity.ok(netWorthService.takeSnapshot(loggedInUser(auth)));
    }

    /**
     * GET /api/net-worth/snapshots
     *
     * Returns all historical net-worth snapshots for the user, sorted by year.
     */
    @GetMapping("/snapshots")
    public ResponseEntity<List<NetWorthSnapshotResponseDto>> getAllSnapshots(Authentication auth) {
        return ResponseEntity.ok(netWorthService.getAllSnapshots(loggedInUser(auth)));
    }
}
