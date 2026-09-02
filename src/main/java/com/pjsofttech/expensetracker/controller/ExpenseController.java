package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.*;
import com.pjsofttech.expensetracker.repository.UserRepository;
import com.pjsofttech.expensetracker.service.ExpenseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/pjsofttech/expense")
@SecurityRequirement(name = "bearerAuth")

public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private UserRepository userRepository;

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

    // ── Get single expense by ID (used by edit form) ──────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> getExpenseById(
            @PathVariable Long id,
            @AuthenticationPrincipal User loggedInUser) {

        return ResponseEntity.ok(expenseService.getExpenseById(id, loggedInUser));
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
            Authentication authentication,
            @PathVariable Long installmentId,
            @RequestBody @Valid InstallmentPaymentRequestDto request) {
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));


        return ResponseEntity.ok(expenseService.addInstallmentPayment(installmentId, request,loggedInUser));
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @GetMapping("/by-category/{id}")
    public ResponseEntity<List<ExpenseResponseDto>> getByCategory(@PathVariable Long id,Authentication authentication) {
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.ok(expenseService.getAllExpensesByCategory(id,loggedInUser));
    }

    @GetMapping("/by-contact/{id}")
    public ResponseEntity<List<ExpenseResponseDto>> getByContact(@PathVariable Long id,Authentication authentication) {
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.ok(expenseService.getAllExpensesByContact(id,loggedInUser));
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

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequestDto req,
            Authentication authentication
    ) {
        User loggedInUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(expenseService.updateExpense(id, req, loggedInUser));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<String> deleteExpense(
            @PathVariable Long expenseId,
            @AuthenticationPrincipal User loggedInUser) {

        return ResponseEntity.ok(
                expenseService.deleteExpense(expenseId, loggedInUser)
        );
    }
}