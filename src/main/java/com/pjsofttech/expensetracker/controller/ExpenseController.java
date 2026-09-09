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
    public ResponseEntity<?> addExpense(
            @RequestBody @Valid ExpenseRequestDto request,
            @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Expense Added", expenseService.addExpense(request, loggedInUser)));
    }

    // ── Get all expenses for logged-in user ───────────────────────────────────
    @GetMapping("/expenses")
    public ResponseEntity<ApiResponse<List<ExpenseResponseDto>>> getAllExpenses(
            @AuthenticationPrincipal User loggedInUser) {

        return ResponseEntity.ok(ApiResponse.success("Expenses Fetched", expenseService.getAllExpenses(loggedInUser)));

    }

    // ── Get single expense by ID (used by edit form) ──────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getExpenseById(
            @PathVariable Long id,
            @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Expense Fetched", expenseService.getExpenseById(id, loggedInUser)));


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
    public ResponseEntity<?> addInstallmentPayment(
            Authentication authentication,
            @PathVariable Long installmentId,
            @RequestBody @Valid InstallmentPaymentRequestDto request) {
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));


        return ResponseEntity.ok(ApiResponse.success("Added Installment Payment", expenseService.addInstallmentPayment(installmentId, request,loggedInUser)));


    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @GetMapping("/by-category/{id}")
    public ResponseEntity<?> getByCategory(@PathVariable Long id,Authentication authentication) {
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.ok(ApiResponse.success("Fetched Expenses By Category", expenseService.getAllExpensesByCategory(id,loggedInUser)));

    }

    @GetMapping("/by-contact/{id}")
    public ResponseEntity<?> getByContact(@PathVariable Long id,Authentication authentication) {
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.ok(ApiResponse.success("Fetched Expenses By Contact", expenseService.getAllExpensesByContact(id,loggedInUser)));
    }

    @GetMapping("/by-payment-type")
    public ResponseEntity<?> getByPaymentType(
            @RequestParam PaymentType type) {
        return ResponseEntity.ok(ApiResponse.success("Fetched Expenses By PaymentType", expenseService.getAllExpensesByPaymentType(type)));

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
    public ResponseEntity<?> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequestDto req,
            Authentication authentication
    ) {
        User loggedInUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Expense Updated", expenseService.updateExpense(id, req, loggedInUser)));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<?> deleteExpense(
            @PathVariable Long expenseId,
            @AuthenticationPrincipal User loggedInUser) {

        return ResponseEntity.ok(ApiResponse.success("Expense Deleted",
                expenseService.deleteExpense(expenseId, loggedInUser)));


    }
}