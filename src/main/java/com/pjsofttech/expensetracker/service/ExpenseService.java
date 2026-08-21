package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.*;
import com.pjsofttech.expensetracker.repository.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private ExpenseInstallmentRepository installmentRepository;
    @Autowired
    private ExpenseInstallmentPaymentRepository paymentRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private BankRepository bankRepository;

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 1 — CREATE EXPENSE
    // ═════════════════════════════════════════════════════════════════════════
    @Transactional
    public ExpenseResponseDto addExpense(@Valid ExpenseRequestDto req, User loggedInUser) {

        // ── 1. Resolve entities ───────────────────────────────────────────────
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + req.getCategoryId()));

        Contact contact = contactRepository.findById(req.getContactId())
                .orElseThrow(() -> new RuntimeException("Contact not found with id: " + req.getContactId()));

//        Bank bank = bankRepository.findById(req.getBankId())
//                .orElseThrow(() -> new RuntimeException("Bank not found with id: " + req.getBankId()));
        Bank bank = null;

        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException(
                        "Bank is required when payment method is BANK_TRANSFER."
                );
            }

            bank = bankRepository.findById(req.getBankId())
                    .orElseThrow(()
                            -> new RuntimeException("Bank not found with id: " + req.getBankId())
                    );
        }
        // ── 2. Calculate total on the backend — never trust frontend total ────
        BigDecimal backendTotal = calculateTotal(req.getAmount(), req.getGstPercentage(), req.getTdsPercentage());

        // ── 3. Build and save expense ─────────────────────────────────────────
        Expense expense = Expense.builder()
                .owner(loggedInUser)
                .contact(contact)
                .category(category)
                .bank(bank)
                .type(req.getType())
                .date(req.getDate())
                .particular(req.getParticular())
                .amount(req.getAmount())
                .gstPercentage(zeroIfNull(req.getGstPercentage()))
                .gstNumber(req.getGstNumber())
                .tdsPercentage(zeroIfNull(req.getTdsPercentage()))
                .total(backendTotal)
                .paymentType(req.getPaymentType())
                .paymentMethod(req.getPaymentMethod())
                .remark(req.getRemark())
                .build();

        // ── 4. Handle payment type ────────────────────────────────────────────
        if (req.getPaymentType() == PaymentType.ONE_TIME) {
            // ONE_TIME: immediately COMPLETE (business rule: payment happens on creation)
            expense.setPaymentStatus(PaymentStatus.COMPLETE);
            expenseRepository.save(expense);

        } else if (req.getPaymentType() == PaymentType.INSTALLMENT) {
            // INSTALLMENT: starts PENDING — no payment recorded yet
            expense.setPaymentStatus(PaymentStatus.PENDING);
            expenseRepository.save(expense);

            // Validate schedule before saving anything
            validateInstallmentSchedule(req, backendTotal);

            // Save all scheduled installments
            List<InstallmentScheduleDto> schedule = req.getInstallments();
            for (InstallmentScheduleDto dto : schedule) {
                ExpenseInstallment installment = ExpenseInstallment.builder()
                        .expense(expense)
                        .installmentNumber(dto.getInstallmentNumber())
                        .dueAmount(dto.getDueAmount().setScale(2, RoundingMode.HALF_UP))
                        .dueDate(dto.getDueDate())
                        .status(InstallmentStatus.PENDING)
                        .build();
                installmentRepository.save(installment);
            }

        } else {
            throw new IllegalArgumentException("Unknown payment type: " + req.getPaymentType());
        }

        // Re-fetch with installments loaded for the response
        Expense saved = expenseRepository.findById(expense.getId())
                .orElseThrow(() -> new RuntimeException("Could not reload expense after save"));
        System.out.println("REQUEST PAYMENT METHOD = " + req.getPaymentMethod());
        System.out.println("ENTITY PAYMENT METHOD  = " + expense.getPaymentMethod());

        return buildExpenseResponse(saved, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 2 — ADD PAYMENT AGAINST A SPECIFIC INSTALLMENT
    //
    // REST design choice: POST /expense/installment/{installmentId}/payment
    //
    // We don't need expenseId in the path because the installment already
    // belongs to exactly one expense. The service still validates the linkage.
    // Using installmentId alone keeps URLs shorter and avoids redundancy.
    // ═════════════════════════════════════════════════════════════════════════
    @Transactional
    public ExpenseResponseDto addInstallmentPayment(Long installmentId,
            @Valid InstallmentPaymentRequestDto req) {

        // ── 1. Resolve installment ────────────────────────────────────────────
        ExpenseInstallment installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new RuntimeException("Installment not found with id: " + installmentId));

        Expense expense = installment.getExpense();

        // ── 2. Guard: can't pay a PAID installment ────────────────────────────
        if (installment.getStatus() == InstallmentStatus.PAID) {
            throw new IllegalArgumentException(
                    "Installment #" + installment.getInstallmentNumber() + " is already fully paid."
            );
        }

        // ── 3. Calculate current pending for this installment ─────────────────
        BigDecimal alreadyPaid = paymentRepository.getTotalPaidByInstallment(installment);
        if (alreadyPaid == null) {
            alreadyPaid = BigDecimal.ZERO;
        }

        BigDecimal pending = installment.getDueAmount().subtract(alreadyPaid);
        if (pending.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Installment #" + installment.getInstallmentNumber() + " has no pending amount."
            );
        }

        // ── 4. Validate payment amount ────────────────────────────────────────
        if (req.getAmount().compareTo(pending) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Payment of ₹%s exceeds installment pending amount of ₹%s.",
                    req.getAmount().toPlainString(), pending.toPlainString()
            ));
        }

        // ── 5. Record the payment ─────────────────────────────────────────────
        ExpenseInstallmentPayment payment = ExpenseInstallmentPayment.builder()
                .installment(installment)
                .amount(req.getAmount().setScale(2, RoundingMode.HALF_UP))
                .paymentDate(req.getDate())
                .remark(req.getRemark())
                .build();

        paymentRepository.save(payment);

        // ── 6. Recalculate and persist installment status ─────────────────────
        BigDecimal newPaid = alreadyPaid.add(req.getAmount());
        if (newPaid.compareTo(BigDecimal.ZERO) <= 0) {
            installment.setStatus(InstallmentStatus.PENDING);
        } else if (newPaid.compareTo(installment.getDueAmount()) >= 0) {
            installment.setStatus(InstallmentStatus.PAID);
        } else {
            installment.setStatus(InstallmentStatus.PARTIAL);
        }
        installmentRepository.save(installment);

        // ── 7. Recalculate and persist expense payment status ─────────────────
        expense.setPaymentStatus(calculateExpensePaymentStatus(expense));
        expenseRepository.save(expense);

        return buildExpenseResponse(expense, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 3 — READ OPERATIONS (all use the same builder)
    // ═════════════════════════════════════════════════════════════════════════
    public List<ExpenseResponseDto> getAllExpenses(User loggedInUser) {
        List<Expense> expenses = expenseRepository.findByOwnerOrderByDateDescIdDesc(loggedInUser);
        AtomicInteger index = new AtomicInteger(1);
        return expenses.stream()
                .map(e -> buildExpenseResponse(e, index))
                .collect(Collectors.toList());
    }

    public List<ExpenseResponseDto> getAllExpensesByCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return mapExpenses(expenseRepository.findByCategory(category));
    }

    public List<ExpenseResponseDto> getAllExpensesByContact(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found with id: " + id));
        return mapExpenses(expenseRepository.findByContact(contact));
    }

    public List<ExpenseResponseDto> getAllExpensesByPaymentType(PaymentType paymentType) {
        return mapExpenses(expenseRepository.findByPaymentType(paymentType));
    }

    public List<ExpenseResponseDto> getAllExpensesByPaymentMethod(PaymentMethod paymentMethod) {
        return mapExpenses(expenseRepository.findByPaymentMethod(paymentMethod));
    }

    public List<ExpenseResponseDto> getAllExpensesByTransactionType(TransactionType type) {
        return mapExpenses(expenseRepository.findByType(type));
    }

    public List<ExpenseResponseDto> getAllExpensesByDate(LocalDate date) {
        return mapExpenses(expenseRepository.findByDate(date));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 4 — SCHEDULE VALIDATION
    // ═════════════════════════════════════════════════════════════════════════
    private void validateInstallmentSchedule(ExpenseRequestDto req, BigDecimal backendTotal) {

        int numberOfInstallments = req.getNumberOfInstallments() == null ? 0 : req.getNumberOfInstallments();

        if (numberOfInstallments <= 0) {
            throw new IllegalArgumentException("Number of installments must be greater than zero.");
        }

        List<InstallmentScheduleDto> installments = req.getInstallments();

        if (installments == null || installments.isEmpty()) {
            throw new IllegalArgumentException("Installment schedule is required.");
        }

        if (installments.size() != numberOfInstallments) {
            throw new IllegalArgumentException(String.format(
                    "Expected %d installments but received %d.",
                    numberOfInstallments, installments.size()
            ));
        }

        Set<Integer> numbers = new HashSet<>();
        BigDecimal scheduleTotal = BigDecimal.ZERO;
        LocalDate previousDate = null;

        for (int i = 0; i < installments.size(); i++) {
            InstallmentScheduleDto dto = installments.get(i);
            int expectedNumber = i + 1;

            // Validate installment number
            if (dto.getInstallmentNumber() == null || dto.getInstallmentNumber() != expectedNumber) {
                throw new IllegalArgumentException(String.format(
                        "Installment numbers must be sequential. Expected %d, got %s.",
                        expectedNumber, dto.getInstallmentNumber()
                ));
            }

            // Duplicate check
            if (!numbers.add(dto.getInstallmentNumber())) {
                throw new IllegalArgumentException(
                        "Duplicate installment number: " + dto.getInstallmentNumber()
                );
            }

            // Amount
            if (dto.getDueAmount() == null || dto.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Installment #" + dto.getInstallmentNumber() + " must have a positive due amount."
                );
            }

            // Date
            if (dto.getDueDate() == null) {
                throw new IllegalArgumentException(
                        "Installment #" + dto.getInstallmentNumber() + " must have a due date."
                );
            }

            // Sequential dates
            if (previousDate != null && !dto.getDueDate().isAfter(previousDate)) {
                throw new IllegalArgumentException(String.format(
                        "Installment #%d due date (%s) must be after installment #%d due date (%s).",
                        dto.getInstallmentNumber(), dto.getDueDate(),
                        dto.getInstallmentNumber() - 1, previousDate
                ));
            }

            previousDate = dto.getDueDate();
            scheduleTotal = scheduleTotal.add(dto.getDueAmount());
        }

        // Schedule total must exactly equal backend-calculated total
        if (scheduleTotal.setScale(2, RoundingMode.HALF_UP)
                .compareTo(backendTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Installment amounts must equal expense total. Total: ₹%s, Scheduled: ₹%s",
                    backendTotal.toPlainString(), scheduleTotal.toPlainString()
            ));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 5 — SHARED CALCULATION HELPERS
    // ═════════════════════════════════════════════════════════════════════════
    /**
     * Calculates: amount + (amount * gst%) - (amount * tds%) Uses HALF_UP
     * rounding to 2 decimal places.
     */
    BigDecimal calculateTotal(BigDecimal amount, BigDecimal gstPct, BigDecimal tdsPct) {
        BigDecimal base = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal gst = zeroIfNull(gstPct);
        BigDecimal tds = zeroIfNull(tdsPct);

        BigDecimal gstAmount = base.multiply(gst).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal tdsAmount = base.multiply(tds).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return base.add(gstAmount).subtract(tdsAmount);
    }

    /**
     * Calculates the GST amount component.
     */
    private BigDecimal calculateGstAmount(BigDecimal amount, BigDecimal gstPct) {
        return amount.multiply(zeroIfNull(gstPct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the TDS amount component.
     */
    private BigDecimal calculateTdsAmount(BigDecimal amount, BigDecimal tdsPct) {
        return amount.multiply(zeroIfNull(tdsPct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates total paid across ALL installments of an expense. For
     * ONE_TIME: returns full total (immediately paid).
     */
    BigDecimal calculateExpensePaidAmount(Expense expense) {
        if (expense.getPaymentType() == PaymentType.ONE_TIME) {
            return expense.getTotal();
        }
        BigDecimal paid = installmentRepository.getTotalPaidByExpense(expense);
        return paid == null ? BigDecimal.ZERO : paid;
    }

    /**
     * Determines the expense payment status from paid vs total.
     */
    PaymentStatus calculateExpensePaymentStatus(Expense expense) {
        BigDecimal paid = calculateExpensePaidAmount(expense);
        BigDecimal total = expense.getTotal();

        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentStatus.PENDING;
        }
        if (paid.compareTo(total) >= 0) {
            return PaymentStatus.COMPLETE;
        }
        return PaymentStatus.PARTIAL;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 6 — RESPONSE BUILDER (single source of truth)
    // ═════════════════════════════════════════════════════════════════════════
    /**
     * Builds a complete ExpenseResponseDto from a persisted Expense. All
     * calculations happen here — never in the controller.
     */
    private ExpenseResponseDto buildExpenseResponse(Expense expense, AtomicInteger indexCounter) {

        BigDecimal amount = expense.getAmount();
        BigDecimal gstPct = zeroIfNull(expense.getGstPercentage());
        BigDecimal tdsPct = zeroIfNull(expense.getTdsPercentage());
        BigDecimal gstAmount = calculateGstAmount(amount, gstPct);
        BigDecimal tdsAmount = calculateTdsAmount(amount, tdsPct);
        BigDecimal total = expense.getTotal();  // already stored correctly
        BigDecimal paid = calculateExpensePaidAmount(expense);
        BigDecimal pending = total.subtract(paid).max(BigDecimal.ZERO);
        PaymentStatus status = calculateExpensePaymentStatus(expense);

        // Build installment list
        List<InstallmentResponseDto> installmentDtos = null;
        if (expense.getPaymentType() == PaymentType.INSTALLMENT) {
            List<ExpenseInstallment> installments
                    = installmentRepository.findByExpenseOrderByInstallmentNumberAsc(expense);

            installmentDtos = installments.stream()
                    .map(this::buildInstallmentResponse)
                    .collect(Collectors.toList());
        }

        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .index(indexCounter.getAndIncrement())
                .date(expense.getDate())
                .type(expense.getType())
                .particular(expense.getParticular())
                .remark(expense.getRemark())
                .contact(ContactResponseDto.builder()
                        .id(expense.getContact().getId())
                        .name(expense.getContact().getName())
                        .email(expense.getContact().getEmail())
                        .phoneNumber(expense.getContact().getPhoneNumber())
                        .build())
                .category(CategoryResponseDto.builder()
                        .id(expense.getCategory().getId())
                        .name(expense.getCategory().getName())
                        .build())
                .bankId(expense.getBank() != null ? expense.getBank().getId() : null).amount(amount)
                .gstPercentage(gstPct)
                .gstAmount(gstAmount)
                .gstNumberStr(expense.getGstNumber())
                .tdsPercentage(tdsPct)
                .tdsAmount(tdsAmount)
                .total(total)
                .paid(paid)
                .pending(pending)
                .paymentType(expense.getPaymentType()) // ALWAYS from entity, never derived
                .paymentMethod(expense.getPaymentMethod())
                .paymentStatus(status)
                .numberOfInstallments(installmentDtos != null ? installmentDtos.size() : null)
                .installments(installmentDtos)
                .build();
    }

    /**
     * Builds an InstallmentResponseDto including full payment history.
     */
    private InstallmentResponseDto buildInstallmentResponse(ExpenseInstallment inst) {
        BigDecimal paid = paymentRepository.getTotalPaidByInstallment(inst);
        if (paid == null) {
            paid = BigDecimal.ZERO;
        }

        BigDecimal pending = inst.getDueAmount().subtract(paid).max(BigDecimal.ZERO);

        InstallmentStatus status;
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            status = InstallmentStatus.PENDING;
        } else if (paid.compareTo(inst.getDueAmount()) >= 0) {
            status = InstallmentStatus.PAID;
        } else {
            status = InstallmentStatus.PARTIAL;
        }

        List<InstallmentPaymentResponseDto> paymentDtos
                = paymentRepository.findByInstallmentOrderByPaymentDateAsc(inst)
                        .stream()
                        .map(p -> InstallmentPaymentResponseDto.builder()
                        .id(p.getId())
                        .amount(p.getAmount())
                        .paymentDate(p.getPaymentDate())
                        .remark(p.getRemark())
                        .build())
                        .collect(Collectors.toList());

        return InstallmentResponseDto.builder()
                .id(inst.getId())
                .installmentNumber(inst.getInstallmentNumber())
                .dueAmount(inst.getDueAmount())
                .paidAmount(paid)
                .pendingAmount(pending)
                .dueDate(inst.getDueDate())
                .status(status)
                .payments(paymentDtos)
                .build();
    }

    private List<ExpenseResponseDto> mapExpenses(List<Expense> expenses) {
        AtomicInteger index = new AtomicInteger(1);
        return expenses.stream()
                .map(e -> buildExpenseResponse(e, index))
                .collect(Collectors.toList());
    }
}
