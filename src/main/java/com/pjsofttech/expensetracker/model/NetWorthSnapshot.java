package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * Point-in-time record of a user's net worth.
 *
 * PURPOSE:
 *   Asset.currentValue and Liability.outstandingAmount represent the user's
 *   CURRENT state only. Snapshots preserve the user's net worth at a specific
 *   point in time.
 *
 * SNAPSHOT BEHAVIOUR:
 *   Multiple snapshots are allowed for the same owner and calendar year.
 *
 *   Example:
 *     2026-01-01 → ₹10,00,000
 *     2026-04-01 → ₹11,50,000
 *     2026-09-21 → ₹14,00,000
 *
 *   Each snapshot is a separate database record.
 *
 * HOW SNAPSHOTS INTERACT WITH PROJECTIONS:
 *   - Historical years → latest snapshot for that year is used.
 *   - Current year     → live net worth is used.
 *   - Future years     → actualNetWorth is null.
 */
@Entity
@Table(name = "net_worth_snapshots"
        //This comment is for one snapshot per year
//       uniqueConstraints = @UniqueConstraint(
//               name = "uq_net_worth_snapshot_owner_year",
//               columnNames = {"owner_id", "year"})
               )
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
