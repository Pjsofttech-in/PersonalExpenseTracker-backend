package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Point-in-time record of a user's net worth for a specific calendar year.
 *
 * PURPOSE:
 *   Asset.currentValue and Liability.outstandingAmount represent the user's
 *   CURRENT state only — they are mutated as financial activity is recorded.
 *   Snapshots preserve what those values were at a specific moment in time,
 *   allowing the projection API to plot "actual" historical net worth.
 *
 * UNIQUENESS:
 *   One snapshot per (owner, year) pair. The service prevents duplicates.
 *   A new snapshot for the same year overwrites the previous one (upsert).
 *
 * HOW SNAPSHOTS INTERACT WITH PROJECTIONS:
 *   - Past years   → actualNetWorth comes from the snapshot for that year.
 *   - Current year → actualNetWorth is calculated live (no snapshot needed).
 *   - Future years → actualNetWorth is null (no data yet).
 */
@Entity
@Table(name = "net_worth_snapshots",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_net_worth_snapshot_owner_year",
               columnNames = {"owner_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetWorthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Calendar year this snapshot represents (e.g. 2025). */
    @Column(nullable = false)
    private Integer year;

    /** The date on which this snapshot was taken. */
    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAssets;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalLiabilities;

    /** netWorth = totalAssets - totalLiabilities (can be negative). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netWorth;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
