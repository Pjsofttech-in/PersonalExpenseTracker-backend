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

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseInstallmentRepository installmentRepository;
    @Autowired private ExpenseInstallmentPaymentRepository paymentRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private BankRepository bankRepository;

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

        Bank bank = null;
        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException("Bank is required when payment method is BANK_TRANSFER.");
            }
            bank = bankRepository.findById(req.getBankId())
                    .orElseThrow(() -> new RuntimeException("Bank not found with id: " + req.getBankId()));
        }

        // ── 2. Calculate total on the backend ─────────────────────────────────
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
            expense.setPaymentStatus(PaymentStatus.COMPLETE);
            expenseRepository.save(expense);

        } else if (req.getPaymentType() == PaymentType.INSTALLMENT) {
            expense.setPaymentStatus(PaymentStatus.PENDING);
            expenseRepository.save(expense);

            validateInstallmentSchedule(req, backendTotal);

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

        Expense saved = expenseRepository.findById(expense.getId())
                .orElseThrow(() -> new RuntimeException("Could not reload expense after save"));

        return buildExpenseResponse(saved, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 2 — GET SINGLE EXPENSE BY ID
    // ═════════════════════════════════════════════════════════════════════════

    public ExpenseResponseDto getExpenseById(Long expenseId, User loggedInUser) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));

        // Security: only the owner can view
        if (expense.getOwner() == null ||
                !expense.getOwner().getId().equals(loggedInUser.getId())) {
            throw new RuntimeException("You are not allowed to view this expense.");
        }

        return buildExpenseResponse(expense, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 3 — ADD PAYMENT AGAINST A SPECIFIC INSTALLMENT
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public ExpenseResponseDto addInstallmentPayment(Long installmentId,
                                                    @Valid InstallmentPaymentRequestDto req) {

        ExpenseInstallment installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new RuntimeException("Installment not found with id: " + installmentId));

        Expense expense = installment.getExpense();

        if (installment.getStatus() == InstallmentStatus.PAID) {
            throw new IllegalArgumentException(
                    "Installment #" + installment.getInstallmentNumber() + " is already fully paid.");
        }

        BigDecimal alreadyPaid = paymentRepository.getTotalPaidByInstallment(installment);
        if (alreadyPaid == null) alreadyPaid = BigDecimal.ZERO;

        BigDecimal pending = installment.getDueAmount().subtract(alreadyPaid);
        if (pending.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Installment #" + installment.getInstallmentNumber() + " has no pending amount.");
        }

        if (req.getAmount().compareTo(pending) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Payment of ₹%s exceeds installment pending amount of ₹%s.",
                    req.getAmount().toPlainString(), pending.toPlainString()));
        }

        ExpenseInstallmentPayment payment = ExpenseInstallmentPayment.builder()
                .installment(installment)
                .amount(req.getAmount().setScale(2, RoundingMode.HALF_UP))
                .paymentDate(req.getDate())
                .remark(req.getRemark())
                .build();
        paymentRepository.save(payment);

        BigDecimal newPaid = alreadyPaid.add(req.getAmount());
        if (newPaid.compareTo(BigDecimal.ZERO) <= 0) {
            installment.setStatus(InstallmentStatus.PENDING);
        } else if (newPaid.compareTo(installment.getDueAmount()) >= 0) {
            installment.setStatus(InstallmentStatus.PAID);
        } else {
            installment.setStatus(InstallmentStatus.PARTIAL);
        }
        installmentRepository.save(installment);

        expense.setPaymentStatus(calculateExpensePaymentStatus(expense));
        expenseRepository.save(expense);

        return buildExpenseResponse(expense, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 4 — READ OPERATIONS
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
    // SECTION 5 — SCHEDULE VALIDATION
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
                    numberOfInstallments, installments.size()));
        }

        Set<Integer> numbers = new HashSet<>();
        BigDecimal scheduleTotal = BigDecimal.ZERO;
        LocalDate previousDate = null;

        for (int i = 0; i < installments.size(); i++) {
            InstallmentScheduleDto dto = installments.get(i);
            int expectedNumber = i + 1;

            if (dto.getInstallmentNumber() == null || dto.getInstallmentNumber() != expectedNumber) {
                throw new IllegalArgumentException(String.format(
                        "Installment numbers must be sequential. Expected %d, got %s.",
                        expectedNumber, dto.getInstallmentNumber()));
            }

            if (!numbers.add(dto.getInstallmentNumber())) {
                throw new IllegalArgumentException("Duplicate installment number: " + dto.getInstallmentNumber());
            }

            if (dto.getDueAmount() == null || dto.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Installment #" + dto.getInstallmentNumber() + " must have a positive due amount.");
            }

            if (dto.getDueDate() == null) {
                throw new IllegalArgumentException(
                        "Installment #" + dto.getInstallmentNumber() + " must have a due date.");
            }

            if (previousDate != null && !dto.getDueDate().isAfter(previousDate)) {
                throw new IllegalArgumentException(String.format(
                        "Installment #%d due date (%s) must be after installment #%d due date (%s).",
                        dto.getInstallmentNumber(), dto.getDueDate(),
                        dto.getInstallmentNumber() - 1, previousDate));
            }

            previousDate = dto.getDueDate();
            scheduleTotal = scheduleTotal.add(dto.getDueAmount());
        }

        if (scheduleTotal.setScale(2, RoundingMode.HALF_UP)
                .compareTo(backendTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Installment amounts must equal expense total. Total: ₹%s, Scheduled: ₹%s",
                    backendTotal.toPlainString(), scheduleTotal.toPlainString()));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 6 — SHARED CALCULATION HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    BigDecimal calculateTotal(BigDecimal amount, BigDecimal gstPct, BigDecimal tdsPct) {
        BigDecimal base = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal gst = zeroIfNull(gstPct);
        BigDecimal tds = zeroIfNull(tdsPct);
        BigDecimal gstAmount = base.multiply(gst).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal tdsAmount = base.multiply(tds).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return base.add(gstAmount).subtract(tdsAmount);
    }

    private BigDecimal calculateGstAmount(BigDecimal amount, BigDecimal gstPct) {
        return amount.multiply(zeroIfNull(gstPct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTdsAmount(BigDecimal amount, BigDecimal tdsPct) {
        return amount.multiply(zeroIfNull(tdsPct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    BigDecimal calculateExpensePaidAmount(Expense expense) {
        if (expense.getPaymentType() == PaymentType.ONE_TIME) {
            return expense.getTotal();
        }
        BigDecimal paid = installmentRepository.getTotalPaidByExpense(expense);
        return paid == null ? BigDecimal.ZERO : paid;
    }

    PaymentStatus calculateExpensePaymentStatus(Expense expense) {
        BigDecimal paid = calculateExpensePaidAmount(expense);
        BigDecimal total = expense.getTotal();
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return PaymentStatus.PENDING;
        if (paid.compareTo(total) >= 0)           return PaymentStatus.COMPLETE;
        return PaymentStatus.PARTIAL;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 7 — RESPONSE BUILDER
    // ═════════════════════════════════════════════════════════════════════════

    private ExpenseResponseDto buildExpenseResponse(Expense expense, AtomicInteger indexCounter) {

        BigDecimal amount    = expense.getAmount();
        BigDecimal gstPct    = zeroIfNull(expense.getGstPercentage());
        BigDecimal tdsPct    = zeroIfNull(expense.getTdsPercentage());
        BigDecimal gstAmount = calculateGstAmount(amount, gstPct);
        BigDecimal tdsAmount = calculateTdsAmount(amount, tdsPct);
        BigDecimal total     = expense.getTotal();
        BigDecimal paid      = calculateExpensePaidAmount(expense);
        BigDecimal pending   = total.subtract(paid).max(BigDecimal.ZERO);
        PaymentStatus status = calculateExpensePaymentStatus(expense);

        List<InstallmentResponseDto> installmentDtos = null;
        if (expense.getPaymentType() == PaymentType.INSTALLMENT) {
            List<ExpenseInstallment> installments =
                    installmentRepository.findByExpenseOrderByInstallmentNumberAsc(expense);
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
                .bankId(expense.getBank() != null ? expense.getBank().getId() : null)
                .amount(amount)
                .gstPercentage(gstPct)
                .gstAmount(gstAmount)
                .gstNumberStr(expense.getGstNumber())
                .tdsPercentage(tdsPct)
                .tdsAmount(tdsAmount)
                .total(total)
                .paid(paid)
                .pending(pending)
                .paymentType(expense.getPaymentType())
                .paymentMethod(expense.getPaymentMethod())
                .paymentStatus(status)
                .numberOfInstallments(installmentDtos != null ? installmentDtos.size() : null)
                .installments(installmentDtos)
                .build();
    }

    private InstallmentResponseDto buildInstallmentResponse(ExpenseInstallment inst) {
        BigDecimal paid = paymentRepository.getTotalPaidByInstallment(inst);
        if (paid == null) paid = BigDecimal.ZERO;

        BigDecimal pending = inst.getDueAmount().subtract(paid).max(BigDecimal.ZERO);

        InstallmentStatus status;
        if (paid.compareTo(BigDecimal.ZERO) <= 0)          status = InstallmentStatus.PENDING;
        else if (paid.compareTo(inst.getDueAmount()) >= 0)  status = InstallmentStatus.PAID;
        else                                                status = InstallmentStatus.PARTIAL;

        List<InstallmentPaymentResponseDto> paymentDtos =
                paymentRepository.findByInstallmentOrderByPaymentDateAsc(inst)
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

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 8 — UPDATE INSTALLMENT SCHEDULE
    // ═════════════════════════════════════════════════════════════════════════

    private void updateInstallmentSchedule(Expense expense, List<InstallmentScheduleDto> requestedSchedule) {

        List<ExpenseInstallment> existing =
                installmentRepository.findByExpenseOrderByInstallmentNumberAsc(expense);

        Map<Integer, ExpenseInstallment> existingByNumber = existing.stream()
                .collect(Collectors.toMap(ExpenseInstallment::getInstallmentNumber, i -> i));

        Set<Integer> requestedNumbers = requestedSchedule.stream()
                .map(InstallmentScheduleDto::getInstallmentNumber)
                .collect(Collectors.toSet());

        for (InstallmentScheduleDto dto : requestedSchedule) {
            ExpenseInstallment installment = existingByNumber.get(dto.getInstallmentNumber());

            if (installment == null) {
                installment = ExpenseInstallment.builder()
                        .expense(expense)
                        .installmentNumber(dto.getInstallmentNumber())
                        .dueAmount(dto.getDueAmount().setScale(2, RoundingMode.HALF_UP))
                        .dueDate(dto.getDueDate())
                        .status(InstallmentStatus.PENDING)
                        .build();
                installmentRepository.save(installment);
            } else {
                BigDecimal paid = paymentRepository.getTotalPaidByInstallment(installment);
                if (paid == null) paid = BigDecimal.ZERO;

                BigDecimal newDue = dto.getDueAmount().setScale(2, RoundingMode.HALF_UP);

                if (newDue.compareTo(paid) < 0) {
                    throw new IllegalArgumentException(
                            "Installment #" + installment.getInstallmentNumber()
                                    + " due amount cannot be less than already paid amount ₹" + paid.toPlainString());
                }

                installment.setDueAmount(newDue);
                installment.setDueDate(dto.getDueDate());

                if (paid.compareTo(BigDecimal.ZERO) <= 0) {
                    installment.setStatus(InstallmentStatus.PENDING);
                } else if (paid.compareTo(newDue) >= 0) {
                    installment.setStatus(InstallmentStatus.PAID);
                } else {
                    installment.setStatus(InstallmentStatus.PARTIAL);
                }
                installmentRepository.save(installment);
            }
        }

        for (ExpenseInstallment old : existing) {
            if (!requestedNumbers.contains(old.getInstallmentNumber())) {
                BigDecimal paid = paymentRepository.getTotalPaidByInstallment(old);
                if (paid == null) paid = BigDecimal.ZERO;

                if (paid.compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalArgumentException(
                            "Cannot remove installment #" + old.getInstallmentNumber()
                                    + " because payments already exist.");
                }
                installmentRepository.delete(old);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 9 — UPDATE EXPENSE
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public ExpenseResponseDto updateExpense(Long expenseId, @Valid ExpenseRequestDto req, User loggedInUser) {

        // ── 1. Find expense ──────────────────────────────────────────────────────
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));

        // ── 2. Security: only owner can edit ─────────────────────────────────────
        if (expense.getOwner() == null ||
                !expense.getOwner().getId().equals(loggedInUser.getId())) {
            throw new RuntimeException("You are not allowed to update this expense.");
        }

        // ── 3. Resolve Category ──────────────────────────────────────────────────
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + req.getCategoryId()));

        // ── 4. Resolve Contact ───────────────────────────────────────────────────
        Contact contact = contactRepository.findById(req.getContactId())
                .orElseThrow(() -> new RuntimeException("Contact not found with id: " + req.getContactId()));

        // ── 5. Resolve Bank ──────────────────────────────────────────────────────
        Bank bank = null;
        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException("Bank is required when payment method is BANK_TRANSFER.");
            }
            bank = bankRepository.findById(req.getBankId())
                    .orElseThrow(() -> new RuntimeException("Bank not found with id: " + req.getBankId()));
        }

        // ── 6. Calculate total on backend ────────────────────────────────────────
        BigDecimal backendTotal = calculateTotal(req.getAmount(), req.getGstPercentage(), req.getTdsPercentage());

        PaymentType oldPaymentType = expense.getPaymentType();
        PaymentType newPaymentType = req.getPaymentType();

        // ── 7. Prevent unsafe payment-type changes ───────────────────────────────

        // INSTALLMENT → ONE_TIME: only allowed if no payments have been made yet
        if (oldPaymentType == PaymentType.INSTALLMENT && newPaymentType == PaymentType.ONE_TIME) {
            BigDecimal existingPaid = installmentRepository
                    .getTotalPaidByExpense(expense);   // use raw repo call, NOT calculateExpensePaidAmount
            if (existingPaid == null) existingPaid = BigDecimal.ZERO;

            if (existingPaid.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException(
                        "Cannot change an installment expense to ONE_TIME because payments already exist.");
            }
        }

        // ONE_TIME → INSTALLMENT: only blocked if actual money was recorded
        // (ONE_TIME expenses are always COMPLETE but have no payment records — so this is always allowed)
        // We keep the guard only as a safety net in case business rules change.
        if (oldPaymentType == PaymentType.ONE_TIME && newPaymentType == PaymentType.INSTALLMENT) {
            // ONE_TIME has no installment payment records by definition — always safe to convert.
            // Guard intentionally left empty; remove this block entirely if not needed.
        }

        // ── 8. Update basic expense fields ───────────────────────────────────────
        expense.setContact(contact);
        expense.setCategory(category);
        expense.setBank(bank);
        expense.setType(req.getType());
        expense.setDate(req.getDate());
        expense.setParticular(req.getParticular());
        expense.setRemark(req.getRemark());
        expense.setAmount(req.getAmount());
        expense.setGstPercentage(zeroIfNull(req.getGstPercentage()));
        expense.setGstNumber(req.getGstNumber());
        expense.setTdsPercentage(zeroIfNull(req.getTdsPercentage()));
        expense.setTotal(backendTotal);
        expense.setPaymentType(newPaymentType);
        expense.setPaymentMethod(req.getPaymentMethod());

        // ── 9. Handle payment type ───────────────────────────────────────────────
        if (newPaymentType == PaymentType.ONE_TIME) {

            expense.setPaymentStatus(PaymentStatus.COMPLETE);
            expenseRepository.save(expense);

            // Remove any unpaid installments left over from a prior INSTALLMENT state
            if (oldPaymentType == PaymentType.INSTALLMENT) {
                List<ExpenseInstallment> installments =
                        installmentRepository.findByExpenseOrderByInstallmentNumberAsc(expense);

                // Double-check no payments exist (already guarded above, but be safe)
                boolean hasPayments = installments.stream().anyMatch(inst -> {
                    BigDecimal p = paymentRepository.getTotalPaidByInstallment(inst);
                    return p != null && p.compareTo(BigDecimal.ZERO) > 0;
                });

                if (hasPayments) {
                    throw new IllegalArgumentException(
                            "Cannot remove installment schedule because payments already exist.");
                }
                installmentRepository.deleteAll(installments);
            }

        } else if (newPaymentType == PaymentType.INSTALLMENT) {

            validateInstallmentSchedule(req, backendTotal);
            expenseRepository.save(expense);
            updateInstallmentSchedule(expense, req.getInstallments());

            expense.setPaymentStatus(calculateExpensePaymentStatus(expense));
            expenseRepository.save(expense);

        } else {
            throw new IllegalArgumentException("Unknown payment type: " + newPaymentType);
        }

        // ── 10. Reload and return ────────────────────────────────────────────────
        Expense updated = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Could not reload expense after update"));

        return buildExpenseResponse(updated, new AtomicInteger(1));
    }
}