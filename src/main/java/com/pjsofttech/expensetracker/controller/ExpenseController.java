package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.*;
import com.pjsofttech.expensetracker.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/pjsofttech/expense")
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    // ── Create expense (with optional installment schedule) ──────────────────
    @PostMapping
    public ResponseEntity<ExpenseResponseDto> addExpense(
            @RequestBody @Valid ExpenseRequestDto request,
            @AuthenticationPrincipal User loggedInUser) {

        return ResponseEntity.ok(expenseService.addExpense(request, loggedInUser));
    }

    // ── Get all expenses for logged-in user ───────────────────────────────────
    @GetMapping("/expenses")
    public ResponseEntity<List<ExpenseResponseDto>> getAllExpenses(
            @AuthenticationPrincipal User loggedInUser) {

        return ResponseEntity.ok(expenseService.getAllExpenses(loggedInUser));
    }

    // ── Add payment against a SPECIFIC installment ────────────────────────────
    //
    // REST design rationale:
    //   POST /expense/installment/{installmentId}/payment
    //
    //   - The installment already holds the expense reference, so expenseId
    //     in the URL would be redundant.
    //   - The service validates the installment belongs to a valid expense.
    //   - This URL clearly expresses: "record a payment against installment X"
    //
    @PostMapping("/installment/{installmentId}/payment")
    public ResponseEntity<ExpenseResponseDto> addInstallmentPayment(
            @PathVariable Long installmentId,
            @RequestBody @Valid InstallmentPaymentRequestDto request) {

        return ResponseEntity.ok(expenseService.addInstallmentPayment(installmentId, request));
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @GetMapping("/by-category/{id}")
    public ResponseEntity<List<ExpenseResponseDto>> getByCategory(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getAllExpensesByCategory(id));
    }

    @GetMapping("/by-contact/{id}")
    public ResponseEntity<List<ExpenseResponseDto>> getByContact(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getAllExpensesByContact(id));
    }

    @GetMapping("/by-payment-type")
    public ResponseEntity<List<ExpenseResponseDto>> getByPaymentType(
            @RequestParam PaymentType type) {
        return ResponseEntity.ok(expenseService.getAllExpensesByPaymentType(type));
    }

    @GetMapping("/by-payment-method")
    public ResponseEntity<List<ExpenseResponseDto>> getByPaymentMethod(
            @RequestParam PaymentMethod method) {
        return ResponseEntity.ok(expenseService.getAllExpensesByPaymentMethod(method));
    }

    @GetMapping("/by-type")
    public ResponseEntity<List<ExpenseResponseDto>> getByTransactionType(
            @RequestParam TransactionType type) {
        return ResponseEntity.ok(expenseService.getAllExpensesByTransactionType(type));
    }

    @GetMapping("/by-date")
    public ResponseEntity<List<ExpenseResponseDto>> getByDate(
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(expenseService.getAllExpensesByDate(date));
    }



}