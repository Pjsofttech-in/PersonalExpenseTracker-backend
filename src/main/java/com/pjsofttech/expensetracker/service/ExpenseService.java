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

/**
 * CHANGES from the original version:
 *   1. Category/Contact/Bank are now fetched with findByIdAndOwner(...),
 *      not plain findById(...) — closes an IDOR gap that let a user
 *      reference another user's category/contact/bank by ID.
 *   2. BankBalanceService is now actually wired in:
 *        - ONE_TIME expense creation  -> deduct `total` once, immediately.
 *        - INSTALLMENT expense creation -> NO bank movement (unchanged —
 *          this was already correct in the original design).
 *        - addInstallmentPayment       -> deduct the payment amount, once,
 *          per payment recorded (this is the only place installment
 *          expenses ever touch the bank).
 *        - deleteExpense               -> reverses whatever was actually
 *          deducted (ONE_TIME: full total; INSTALLMENT: sum of payments
 *          actually made) before removing the row.
 *        - updateExpense               -> for ONE_TIME expenses, if
 *          amount/bank changes after creation, reverses the old bank debit
 *          and reapplies the new one inside the same transaction.
 */
@Service
public class ExpenseService {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseInstallmentRepository installmentRepository;
    @Autowired private ExpenseInstallmentPaymentRepository paymentRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private BankRepository bankRepository;
    @Autowired private BankBalanceService bankBalanceService;



    private void applyBankMovement(
            TransactionType type,
            Bank bank,
            BigDecimal amount) {

        if (bank == null || amount == null) {
            return;
        }

        if (type == TransactionType.EXPENSE) {

            bankBalanceService.deductAmount(
                    bank,
                    amount
            );

        } else if (type == TransactionType.INCOME) {

            bankBalanceService.addAmount(
                    bank,
                    amount
            );
        }
    }

    //Helper if INCOME THEN deduct amount from bank after income deleted
    //and if EXPENSE THEN ADD AMOUNT TO BANK AFTER EXPENSE DELETED
    private void reverseBankMovement(
            TransactionType type,
            Bank bank,
            BigDecimal amount) {

        if (bank == null || amount == null) {
            return;
        }

        if (type == TransactionType.EXPENSE) {

            // Original expense deducted money.
            // Reverse = add money back.
            bankBalanceService.addAmount(
                    bank,
                    amount
            );

        } else if (type == TransactionType.INCOME) {

            // Original income added money.
            // Reverse = deduct money.
            bankBalanceService.deductAmount(
                    bank,
                    amount
            );
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 1 — CREATE EXPENSE
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public ExpenseResponseDto addExpense(@Valid ExpenseRequestDto req, User loggedInUser) {

        if (req.getType() == null) {
            throw new IllegalArgumentException(
                    "Transaction type is required."
            );
        }
        // ── 1. Resolve entities, scoped to the owner ───────────────────────────
        Category category = categoryRepository.findByIdAndOwner(req.getCategoryId(), loggedInUser)
                .orElseThrow(() -> new RuntimeException("Category not found or does not belong to user."));

        if (category.getTransactionType() != req.getType()) {
            throw new IllegalArgumentException(
                    "Selected category does not belong to transaction type " + req.getType()
            );
        }

//        Contact contact = contactRepository.findByIdAndOwner(req.getContactId(), loggedInUser)
//                .orElseThrow(() -> new RuntimeException("Contact not found or does not belong to user."));
//Contact is OPTIONAL
        Contact contact = null;

        if (req.getContactId() != null) {
            contact = contactRepository.findByIdAndOwner(req.getContactId(), loggedInUser)
                    .orElseThrow(() ->
                            new RuntimeException("Contact not found or does not belong to user."));
        }

        Bank bank = null;
        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException("Bank is required when payment method is BANK_TRANSFER.");
            }
            bank = bankRepository.findByIdAndOwner(req.getBankId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException("Bank not found or does not belong to user."));
        }

        // ── 2. Calculate total on the backend ─────────────────────────────────
        BigDecimal backendTotal = calculateTotal(req.getAmount(), req.getGstPercentage(), req.getTdsPercentage());

        // ── 3. Build expense ────────────────────────────────────────────────────
        Expense expense = Expense.builder()
                .owner(loggedInUser)
                .contact(contact)
                .category(category)
                .bank(bank)
                .type(req.getType())
                .sourceType(ExpenseSourceType.MANUAL)
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

            // Real cash leaves the bank now, exactly once, for the full total
//            EXPENSE + BANK_TRANSFER
//                    ↓
//            Bank balance - total
            //INCOME + BANK_TRANSFER
            //        ↓
            //Bank balance + total
            // (amount + GST - TDS = actual cash paid out).
            if (bank != null) {

                if (req.getType() == TransactionType.EXPENSE) {

                    bankBalanceService.deductAmount(
                            bank,
                            backendTotal
                    );

                } else if (req.getType() == TransactionType.INCOME) {

                    bankBalanceService.addAmount(
                            bank,
                            backendTotal
                    );
                }
            }

        }



        else if (req.getPaymentType() == PaymentType.INSTALLMENT) {
            //INCOME
            // └── ONE_TIME       ✅
            //
            //INCOME
            // └── INSTALLMENT    ❌
            //EXPENSE
            // ├── ONE_TIME       ✅
            // └── INSTALLMENT    ✅
             if (req.getType() == TransactionType.INCOME
                    && req.getPaymentType() == PaymentType.INSTALLMENT) {

                throw new IllegalArgumentException(
                        "Income cannot be recorded as an installment."
                );
            }

            // No bank movement here — nothing has actually been paid yet.
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

        if (expense.getOwner() == null || !expense.getOwner().getId().equals(loggedInUser.getId())) {
            throw new RuntimeException("You are not allowed to view this expense.");
        }

        return buildExpenseResponse(expense, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 3 — ADD PAYMENT AGAINST A SPECIFIC INSTALLMENT
    //             (the only point where an INSTALLMENT expense touches the bank)
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public ExpenseResponseDto addInstallmentPayment(Long installmentId,
                                                    @Valid InstallmentPaymentRequestDto req,
                                                    User loggedInUser) {

        ExpenseInstallment installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new RuntimeException("Installment not found with id: " + installmentId));

        Expense expense = installment.getExpense();

        if (expense.getOwner() == null || !expense.getOwner().getId().equals(loggedInUser.getId())) {
            throw new RuntimeException("You are not allowed to modify this installment.");
        }

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

        // Real cash leaves the bank now, exactly once, for this payment only.
        if (expense.getBank() != null) {
            bankBalanceService.deductAmount(expense.getBank(), payment.getAmount());
        }

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

    public List<ExpenseResponseDto> getAllExpensesByCategory(Long id, User loggedInUser) {
        Category category = categoryRepository.findByIdAndOwner(id, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Category not found or does not belong to user."));
        return mapExpenses(expenseRepository.findByCategory(category));
    }

    public List<ExpenseResponseDto> getAllExpensesByContact(Long id, User loggedInUser) {
        Contact contact = contactRepository.findByIdAndOwner(id, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Contact not found or does not belong to user."));
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
        ContactResponseDto contactResponseDto = null;
        if ( expense.getContact() !=null){
            contactResponseDto =  ContactResponseDto.builder()
                    .id(expense.getContact().getId()!=null ? expense.getContact().getId() : null)
                    .name(expense.getContact().getName())
                    .email(expense.getContact().getEmail())
                    .phoneNumber(expense.getContact().getPhoneNumber())
                    .build();
        }

        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .index(indexCounter.getAndIncrement())
                .date(expense.getDate())
                .type(expense.getType())
                .sourceType(expense.getSourceType())
                .particular(expense.getParticular())
                .remark(expense.getRemark())
                .contact(contactResponseDto)


//                .category(CategoryResponseDto.builder()
//                        .id(expense.getCategory().getId())
//                        .name(expense.getCategory().getName())
//                        .build())
                .category(
                        expense.getCategory() != null
                                ? CategoryResponseDto.builder()
                                .id(expense.getCategory().getId())
                                .name(expense.getCategory().getName())
                                .build()
                                : null
                )
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

        if (expense.getSourceType() == ExpenseSourceType.ASSET_PURCHASE) {
            throw new IllegalArgumentException(
                    "Asset purchase expenses must be edited from the Asset section."
            );
        }
        // ── 2. Security: only owner can edit ─────────────────────────────────────
        if (expense.getOwner() == null || !expense.getOwner().getId().equals(loggedInUser.getId())) {
            throw new RuntimeException("You are not allowed to update this expense.");
        }
        if (req.getType() == null) {
            throw new IllegalArgumentException(
                    "Transaction type is required."
            );
        }
        // ── 1. Resolve entities, scoped to the owner ───────────────────────────
        Category category = categoryRepository.findByIdAndOwner(req.getCategoryId(), loggedInUser)
                .orElseThrow(() -> new RuntimeException("Category not found or does not belong to user."));

        if (category.getTransactionType() != req.getType()) {
            throw new IllegalArgumentException(
                    "Selected category does not belong to transaction type " + req.getType()
            );
        }

        // ── 3. Resolve Category/Contact/Bank, scoped to owner ────────────────────
//        Category category = categoryRepository.findByIdAndOwner(req.getCategoryId(), loggedInUser)
//                .orElseThrow(() -> new RuntimeException("Category not found or does not belong to user."));

//        Contact contact = contactRepository.findByIdAndOwner(req.getContactId(), loggedInUser)
//                .orElseThrow(() -> new RuntimeException("Contact not found or does not belong to user."));

        //Contact is OPTIONAL
        Contact contact = null;

        if (req.getContactId() != null) {
            contact = contactRepository.findByIdAndOwner(req.getContactId(), loggedInUser)
                    .orElseThrow(() ->
                            new RuntimeException("Contact not found or does not belong to user."));
        }

        Bank newBank = null;
        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException("Bank is required when payment method is BANK_TRANSFER.");
            }
            newBank = bankRepository.findByIdAndOwner(req.getBankId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException("Bank not found or does not belong to user."));
        }

        // ── 4. Calculate total on backend ────────────────────────────────────────
        BigDecimal backendTotal = calculateTotal(req.getAmount(), req.getGstPercentage(), req.getTdsPercentage());

        PaymentType oldPaymentType = expense.getPaymentType();
        PaymentType newPaymentType = req.getPaymentType();

        Bank oldBank = expense.getBank();
        BigDecimal oldTotal = expense.getTotal();

        // ── 5. Prevent unsafe payment-type changes ───────────────────────────────
        if (oldPaymentType == PaymentType.INSTALLMENT && newPaymentType == PaymentType.ONE_TIME) {
            BigDecimal existingPaid = installmentRepository.getTotalPaidByExpense(expense);
            if (existingPaid == null) existingPaid = BigDecimal.ZERO;

            if (existingPaid.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException(
                        "Cannot change an installment expense to ONE_TIME because payments already exist.");
            }
        }
        TransactionType oldType = expense.getType();


        // ── 6. Update basic expense fields ───────────────────────────────────────
        expense.setContact(contact);
        expense.setCategory(category);
        expense.setBank(newBank);
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

        // ── 7. Handle payment type + bank reversal/reapply ───────────────────────
        if (newPaymentType == PaymentType.ONE_TIME) {

            // If this expense was ALREADY a settled ONE_TIME expense (money already
            // moved), reverse the old bank debit before applying the new one —
            // otherwise every edit would double-deduct.
            if (oldPaymentType == PaymentType.ONE_TIME
                    && expense.getPaymentStatus() == PaymentStatus.COMPLETE
                    && oldBank != null) {
//                bankBalanceService.addAmount(oldBank, oldTotal);
                reverseBankMovement(
                        oldType,
                        oldBank,
                        oldTotal
                );
            }

            expense.setPaymentStatus(PaymentStatus.COMPLETE);
            expenseRepository.save(expense);

            // Apply the new transaction's bank movement

            if (newBank != null) {
//                bankBalanceService.deductAmount(newBank, backendTotal);
                applyBankMovement(
                        req.getType(),
                        newBank,
                        backendTotal
                );
            }

            // Remove old installment schedule if required
            // Remove any unpaid installments left over from a prior INSTALLMENT state
            if (oldPaymentType == PaymentType.INSTALLMENT) {
                List<ExpenseInstallment> installments =
                        installmentRepository.findByExpenseOrderByInstallmentNumberAsc(expense);

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

            // INSTALLMENT expenses never hold a bank debit at the expense level —
            // any money already paid lives in ExpenseInstallmentPayment rows and
            // was already deducted when each payment was recorded. Nothing to
            // reverse/reapply here for the schedule itself.
            validateInstallmentSchedule(req, backendTotal);
            expenseRepository.save(expense);
            updateInstallmentSchedule(expense, req.getInstallments());

            expense.setPaymentStatus(calculateExpensePaymentStatus(expense));
            expenseRepository.save(expense);

        } else {
            throw new IllegalArgumentException("Unknown payment type: " + newPaymentType);
        }

        // ── 8. Reload and return ────────────────────────────────────────────────
        Expense updated = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Could not reload expense after update"));

        return buildExpenseResponse(updated, new AtomicInteger(1));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECTION 10 — DELETE EXPENSE
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public String deleteExpense(Long expenseId, User loggedInUser) {


        // 1. Find expense
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));
        if (expense.getSourceType() == ExpenseSourceType.ASSET_PURCHASE) {
            throw new IllegalArgumentException(
                    "Asset purchase expenses cannot be deleted directly. Delete the asset instead."
            );
        }

        // 2. Security: only owner can delete
        if (expense.getOwner() == null || !expense.getOwner().getId().equals(loggedInUser.getId())) {
            throw new RuntimeException("You are not allowed to delete this expense.");
        }

        // 3. Reverse whatever cash actually moved, before touching any rows.
        if (expense.getPaymentType() == PaymentType.ONE_TIME) {

            if (expense.getBank() != null && expense.getPaymentStatus() == PaymentStatus.COMPLETE) {
                reverseBankMovement(
                        expense.getType(),
                        expense.getBank(),
                        expense.getTotal()
                );
            }

        } else if (expense.getPaymentType() == PaymentType.INSTALLMENT) {

            List<ExpenseInstallment> installments =
                    installmentRepository.findByExpenseOrderByInstallmentNumberAsc(expense);

            for (ExpenseInstallment installment : installments) {

                List<ExpenseInstallmentPayment> payments =
                        paymentRepository.findByInstallmentOrderByPaymentDateAsc(installment);

                if (payments != null && !payments.isEmpty()) {

                        BigDecimal totalPaidThisInstallment = payments.stream()
                                .map(ExpenseInstallmentPayment::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (expense.getBank() != null) {
                        bankBalanceService.addAmount(
                                expense.getBank(),
                                totalPaidThisInstallment
                        );
                    }

                    paymentRepository.deleteAll(payments);
                }
            }

            if (!installments.isEmpty()) {
                installmentRepository.deleteAll(installments);
            }
        }

        // 4. Delete expense
        expenseRepository.delete(expense);

        return "Expense Deleted Successfully";
    }



    @Transactional
    public Expense createAssetPurchaseExpense(
            Asset asset,
            AssetAcquisition acquisition,
            User loggedInUser) {

        Bank bank = acquisition.getBank();

        Expense expense = Expense.builder()
                .owner(loggedInUser)
                .asset(asset)
                       .contact(asset.getContact())
                       .category(null)
                .bank(bank)
                .type(TransactionType.EXPENSE)
                .sourceType(ExpenseSourceType.ASSET_PURCHASE)
                .date(asset.getPurchaseDate().atStartOfDay())
                .particular("Purchase - " + asset.getName())
                .amount(acquisition.getAmount())
                .gstPercentage(BigDecimal.ZERO)
                .tdsPercentage(BigDecimal.ZERO)
                .total(acquisition.getAmount())
                .paymentType(PaymentType.ONE_TIME)
                .paymentMethod(acquisition.getPaymentMethod())
                .paymentStatus(PaymentStatus.COMPLETE)
                .remark(acquisition.getRemark())
                .build();

        expenseRepository.save(expense);

        // Link both sides
        asset.setPurchaseExpense(expense);
        expense.setAsset(asset);

        // Only actual bank-funded purchases move bank balance.
        if (bank != null) {
            bankBalanceService.deductAmount(
                    bank,
                    acquisition.getAmount()
            );
        }

        return expense;
    }
}