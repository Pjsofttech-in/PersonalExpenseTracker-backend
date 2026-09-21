package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores a user's net-worth target.
 *
 * IMPORTANT — targetAmount semantics:
 *   targetAmount is the NOMINAL amount the user wants to have in targetYear.
 *   It is NOT automatically inflated from today's purchasing power.
 *   inflationRate is used only for projecting future expenses and for
 *   financial projection assumptions, not for adjusting targetAmount.
 *
 * A user may have at most one ACTIVE target (enforced by the service layer).
 */
@Entity
@Table(name = "net_worth_targets",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_net_worth_target_owner_active",
               columnNames = {"owner_id", "active"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetWorthTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who owns this target. Never set from the request body. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Nominal net-worth amount the user wants to reach by targetYear.
     * This is NOT adjusted for inflation — it is the raw number the user entered.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    /** Calendar year in which the user wants to hit targetAmount. */
    @Column(nullable = false)
    private Integer targetYear;

    /**
     * Annual inflation rate (percentage, e.g. 6.0 for 6 %).
     * Used for projecting future expense growth and for projection assumptions.
     * NOT used to inflate targetAmount.
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal inflationRate;

    /**
     * Only one target may be active per user at a time.
     * The service deactivates any prior active target before creating a new one.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
