package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.ExpenseInstallment;
import com.pjsofttech.expensetracker.model.ExpenseInstallmentPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseInstallmentPaymentRepository extends JpaRepository<ExpenseInstallmentPayment, Long> {

    List<ExpenseInstallmentPayment> findByInstallmentOrderByPaymentDateAsc(ExpenseInstallment installment);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM ExpenseInstallmentPayment p
        WHERE p.installment = :installment
    """)
    BigDecimal getTotalPaidByInstallment(@Param("installment") ExpenseInstallment installment);
}