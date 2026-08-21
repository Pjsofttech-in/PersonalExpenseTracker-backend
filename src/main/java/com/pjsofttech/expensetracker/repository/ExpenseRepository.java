package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
