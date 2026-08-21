package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single PAYMENT made against a scheduled installment.
 *
 * One installment can have multiple payments (partial payments).
 *
 * Example:
 *   Installment due = ₹500
 *   Payment 1 = ₹100 (on 01/08/2026)
 *   Payment 2 = ₹150 (on 10/08/2026)
 *   Payment 3 = ₹250 (on 15/08/2026)
 *   → Installment PAID
 *
 * Payment history is preserved — never overwritten.
 */
@Entity
@Table(name = "expense_installment_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseInstallmentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false)
    private ExpenseInstallment installment;

    /** Amount paid in this single payment. */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "remark")
    private String remark;
}