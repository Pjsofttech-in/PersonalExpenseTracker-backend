package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Expense;
import com.pjsofttech.expensetracker.model.ExpenseInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseInstallmentRepository extends JpaRepository<ExpenseInstallment, Long> {

    List<ExpenseInstallment> findByExpenseOrderByInstallmentNumberAsc(Expense expense);

    Optional<ExpenseInstallment> findByIdAndExpense(Long id, Expense expense);

    /**
     * Total paid across ALL installments of an expense.
     * Uses SUM of payment amounts, not dueAmount.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM ExpenseInstallmentPayment p
        WHERE p.installment.expense = :expense
    """)
    BigDecimal getTotalPaidByExpense(@Param("expense") Expense expense);
}

