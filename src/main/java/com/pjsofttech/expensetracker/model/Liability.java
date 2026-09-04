package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents money owed by the user (a debt).
 *
 * outstandingAmount is NEVER set directly by client requests.
 * It is only ever mutated by:
 *   - LiabilityService.recordLoanReceived()   (increases it, at creation)
 *   - LiabilityService.recordPayment()        (decreases it, by the principal component)
 *
 * A Liability never creates an Expense by itself. Only the interest portion
 * of a LiabilityPayment creates an Expense (see LiabilityPayment / LiabilityService).
 */
@Entity
@Table(name = "liabilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Liability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─────────────────────────────────────────────
    // OWNER
    // ─────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // ─────────────────────────────────────────────
    // BASIC INFORMATION
    // ─────────────────────────────────────────────

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiabilityGroup liabilityGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiabilityType type;

    /** Who the money is owed to — bank, NBFC, individual, etc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact lender;

    @Column(name = "description", length = 1000)
    private String description;

    // ─────────────────────────────────────────────
    // AMOUNTS
    // ─────────────────────────────────────────────

    /**
     * Original amount borrowed. Immutable once any payment has been recorded
     * (enforced in LiabilityService, not here).
     */
    @Column(name = "original_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalAmount;
    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private BigDecimal depositAmount;


    /**
     * Remaining amount owed. Derived: principalAmount - SUM(payment.principalComponent).
     * Never exposed as a directly-settable field in the request DTO.
     */
    @Column(name = "outstanding_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    // ─────────────────────────────────────────────
    // DATES
    // ─────────────────────────────────────────────

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // ─────────────────────────────────────────────
    // BANK — where loan proceeds were deposited (nullable: e.g. cash loan,
    // or loan taken purely to fund an asset purchase with no bank leg)
    // ─────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    // ─────────────────────────────────────────────
    // STATUS
    // ─────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LiabilityStatus status = LiabilityStatus.ACTIVE;

    // ─────────────────────────────────────────────
    // PAYMENTS
    // ─────────────────────────────────────────────

    @OneToMany(mappedBy = "liability", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LiabilityPayment> payments = new ArrayList<>();

    // ─────────────────────────────────────────────
    // AUDIT
    // ─────────────────────────────────────────────

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.outstandingAmount == null) {
            this.outstandingAmount = this.originalAmount;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}