package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense,Long> {
    List<Expense> findByCategory(Category category);
    List<Expense> findByType(TransactionType transactionType);
    List<Expense> findByPaymentStatus(PaymentStatus paymentStatus);
    List<Expense> findByContact(Contact contact);
    List<Expense> findByDate(LocalDate date);
    List<Expense> findByOwner(User owner);
    List<Expense> findByOwnerId(Long ownerId);
    List<Expense> findByPaymentType(PaymentType paymentType);

    List<Expense> findByOwnerOrderByDateDesc(User loggedInUser);

    List<Expense> findByDateBetween(LocalDateTime start, LocalDateTime end);

    List<Expense> findByPaymentMethod(PaymentMethod paymentMethodRequestDto);
    List<Expense> findByOwnerOrderByDateDescIdDesc(User loggedInUser);
    void deleteByContact_Id(Long contactId);
    // Total of all EXPENSE-type entries for the user in a date range.
    // Used by the projection engine to calculate the user's annual spending.
            @Query("""
           SELECT COALESCE(SUM(e.total), 0)
           FROM Expense e
           WHERE e.owner      = :owner
             AND e.type       = com.pjsofttech.expensetracker.model.TransactionType.EXPENSE
             AND e.date       >= :from
             AND e.date       < :to
           """)
            BigDecimal getTotalExpenseByOwnerAndDateRange(
            @Param("owner") User owner,
            @Param("from")  LocalDateTime from,
            @Param("to")    LocalDateTime to);

    @Query("""
       SELECT COALESCE(SUM(e.total), 0)
       FROM Expense e
       WHERE e.owner = :owner
         AND e.type = com.pjsofttech.expensetracker.model.TransactionType.INCOME
         AND e.date >= :from
         AND e.date < :to
       """)
    BigDecimal getTotalIncomeByOwnerAndDateRange(
            @Param("owner") User owner,
            @Param("from")  LocalDateTime from,
            @Param("to")    LocalDateTime to);
}
