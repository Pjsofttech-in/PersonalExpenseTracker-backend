package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single real-world payment made against a Liability (e.g. one EMI).
 *
 * Splits into:
 *   - principalComponent: reduces Liability.outstandingAmount, no Expense impact
 *   - interestComponent : creates/links a real Expense, no Liability impact
 *
 * totalAmount = principalComponent + interestComponent, and is exactly what
 * gets debited from the bank, once, per payment.
 *
 * This intentionally does NOT reuse ExpenseInstallmentPayment — a liability
 * payment has two components with different downstream effects, whereas an
 * expense installment payment has exactly one.
 */
@Entity
@Table(name = "liability_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiabilityPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liability_id", nullable = false)
    private Liability liability;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Portion that reduces the liability's outstanding amount. May be zero (interest-only payment). */
    @Column(name = "principal_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalComponent;

    /** Portion that becomes a real Expense (cost of borrowing). May be zero (principal-only payment). */
    @Column(name = "interest_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestComponent;

    /** principalComponent + interestComponent — the actual cash debited from the bank. */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    /** Bank the payment was made from. Nullable only for CASH payment method. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    /**
     * The Expense row auto-created for the interest component, if any.
     * Null when interestComponent = 0 (pure principal repayment).
     * Deleting a LiabilityPayment must also delete/reverse this linked Expense.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_expense_id", unique = true)
    private Expense interestExpense;

    @Column(name = "remark")
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}