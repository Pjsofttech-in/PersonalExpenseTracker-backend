package com.pjsofttech.expensetracker.repository;

/**
 * ADD this method to the existing ExpenseRepository interface.
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 *   import com.pjsofttech.expensetracker.model.User;
 *   import org.springframework.data.jpa.repository.Query;
 *   import org.springframework.data.repository.query.Param;
 *   import java.math.BigDecimal;
 *   import java.time.LocalDateTime;
 *
 *   // Total of all EXPENSE-type entries for the user in a date range.
 *   // Used by the projection engine to calculate the user's annual spending.
 *   @Query("""
 *          SELECT COALESCE(SUM(e.total), 0)
 *          FROM Expense e
 *          WHERE e.owner      = :owner
 *            AND e.type       = com.pjsofttech.expensetracker.model.TransactionType.EXPENSE
 *            AND e.date       >= :from
 *            AND e.date       < :to
 *          """)
 *   BigDecimal getTotalExpenseByOwnerAndDateRange(
 *           @Param("owner") User owner,
 *           @Param("from")  LocalDateTime from,
 *           @Param("to")    LocalDateTime to);
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 * This file is documentation only — it will not compile as-is.
 * Merge the annotation and method signature into ExpenseRepository.java.
 */
public class ExpenseRepositoryAdditions { /* documentation only */ }
