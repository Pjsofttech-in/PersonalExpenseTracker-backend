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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LiabilityService {
    @Autowired private LiabilityRepository liabilityRepository;
    @Autowired private LiabilityPaymentRepository liabilityPaymentRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private BankRepository bankRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private BankBalanceService bankBalanceService;

    // ═════════════════════════════════════════════════════════════════════
    // CREATE LIABILITY (+ optional loan deposit into a bank)
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public LiabilityResponseDto addLiability(@Valid LiabilityRequestDto req, User loggedInUser) {

        Contact lender = null;
        if (req.getLenderContactId() != null) {
            lender = contactRepository.findByIdAndOwner(req.getLenderContactId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException("Contact not found or does not belong to user."));
        }


        Bank depositBank = null;

        if (req.getDepositBankId() != null) {
            depositBank = bankRepository
                    .findByIdAndOwner(req.getDepositBankId(), loggedInUser)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Bank not found or does not belong to user."));
        }

        if (req.getDepositBankId() != null) {
            depositBank = bankRepository.findByIdAndOwner(req.getDepositBankId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException("Bank not found or does not belong to user."));
        }

        BigDecimal originalAmount = req.getOriginalAmount().setScale(2, RoundingMode.HALF_UP);
        if (req.getType().getGroup() != req.getLiabilityGroup()) {
            throw new IllegalArgumentException(
                    "Liability type does not belong to the selected liability group."
            );
        }
        if(req.getDepositAmount()!=null){
            if (req.getDepositAmount   ().compareTo(originalAmount) > 0) {
                throw new IllegalArgumentException("Deposit amount cannot exceed original amount.");
            }
        }

        Liability liability = Liability.builder()
                .owner(loggedInUser)
                .name(req.getName())
                .liabilityGroup(req.getLiabilityGroup())
                .type(req.getType())
                .lender(lender)
                .description(req.getDescription())
                .originalAmount(originalAmount)
                .depositAmount(req.getDepositAmount())
                .outstandingAmount(originalAmount) // loan received in full -> full principal outstanding
                .interestRate(req.getInterestRate())
                .startDate(req.getStartDate())
                .dueDate(req.getDueDate())
                .bank(depositBank)
                .status(LiabilityStatus.ACTIVE)
                .build();

        Liability saved = liabilityRepository.save(liability);

        // ── Loan received: increase liability (done above) + optionally credit bank.
        //    This is the ONLY place a loan credits a bank. It never creates an Expense.
        if (depositBank != null) {
            BigDecimal depositAmount = req.getDepositAmount() != null
                    ? req.getDepositAmount().setScale(2, RoundingMode.HALF_UP)
                    : originalAmount;

            if (depositAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Deposit amount cannot be negative.");
            }

            if (depositAmount.compareTo(originalAmount) > 0) {
                throw new IllegalArgumentException("Deposit amount cannot exceed principal amount.");
            }

            bankBalanceService.addAmount(depositBank, depositAmount);
        }

        return mapToResponse(saved);
    }

    // ═════════════════════════════════════════════════════════════════════
    // READ
    // ═════════════════════════════════════════════════════════════════════

    public List<LiabilityResponseDto> getAllLiabilities(User loggedInUser) {
        return liabilityRepository.findByOwnerOrderByStartDateDescIdDesc(loggedInUser)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public LiabilityResponseDto getLiabilityById(Long id, User loggedInUser) {
        return mapToResponse(findLiability(id, loggedInUser));
    }

    // ═════════════════════════════════════════════════════════════════════
    // RECORD PAYMENT (principal + interest split — e.g. an EMI)
    // ═════════════════════════════════════════════════════════════════════
    private Bank resolvePaymentBank(
            LiabilityPaymentRequestDto req,
            User loggedInUser) {

        if (req.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {

            if (req.getBankId() != null) {
                throw new IllegalArgumentException(
                        "bankId should only be provided for BANK_TRANSFER.");
            }

            return null;
        }

        if (req.getBankId() == null) {
            throw new IllegalArgumentException(
                    "bankId is required for BANK_TRANSFER.");
        }

        return bankRepository
                .findByIdAndOwner(req.getBankId(), loggedInUser)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Bank not found or does not belong to user."));
    }
    private Expense createInterestExpense(
            Liability liability,
            BigDecimal interest,
            Bank bank,
            LiabilityPaymentRequestDto req,
            User loggedInUser) {

        // Interest expense requires a category
        if (req.getInterestCategoryId() == null) {
            throw new IllegalArgumentException(
                    "interestCategoryId is required when interestComponent > 0.");
        }

        Category interestCategory = categoryRepository
                .findByIdAndOwner(
                        req.getInterestCategoryId(),
                        loggedInUser
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Interest category not found or does not belong to user."
                        ));

        // Lender is used as the Expense contact
        Contact lender = liability.getLender();

//        if (lender == null) {
//            throw new IllegalArgumentException(
//                    "Lender contact is required when recording loan interest."
//            );
//        }

        BigDecimal interestAmount =
                interest.setScale(2, RoundingMode.HALF_UP);

        Expense expense = Expense.builder()
                .owner(loggedInUser)

                // Expense classification
                .category(interestCategory)
                .contact(lender)

                // Where the EMI/payment was paid from
                .bank(bank)

                .type(TransactionType.EXPENSE)
                .sourceType(ExpenseSourceType.LIABILITY_PAYMENT)

                .date(req.getDate().atStartOfDay())

                .particular("Interest - " + liability.getName())

                // Only interest is an expense.
                // Principal is NOT an expense.
                .amount(interestAmount)

                .gstPercentage(BigDecimal.ZERO)
                .tdsPercentage(BigDecimal.ZERO)

                .total(interestAmount)

                .paymentType(PaymentType.ONE_TIME)
                .paymentMethod(req.getPaymentMethod())
                .paymentStatus(PaymentStatus.COMPLETE)

                .remark(req.getRemark())

                .build();

        return expenseRepository.save(expense);
    }



    @Transactional
    private LiabilityResponseDto recordLoanPayment(
            Liability liability,
            LiabilityPaymentRequestDto req,
            User loggedInUser) {

        BigDecimal principal = req.getPrincipalComponent()
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal interest = req.getInterestComponent()
                .setScale(2, RoundingMode.HALF_UP);

        if (principal.compareTo(BigDecimal.ZERO) == 0
                && interest.compareTo(BigDecimal.ZERO) == 0) {

            throw new IllegalArgumentException(
                    "Payment must have a non-zero principal or interest.");
        }
        if (principal.compareTo(BigDecimal.ZERO) < 0
                || interest.compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Principal and interest cannot be negative.");
        }

        if (principal.compareTo(liability.getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Principal component exceeds outstanding amount.");
        }

        BigDecimal total = principal.add(interest);

        Bank bank = resolvePaymentBank(req, loggedInUser);

//        Bank bank = bankRepository.findById(req.getBankId())
//                .orElseThrow(()->new RuntimeException("Bank Not Found"));

        Expense interestExpense = null;

        if (interest.compareTo(BigDecimal.ZERO) > 0) {

            interestExpense = createInterestExpense(
                    liability,
                    interest,
                    bank,
                    req,
                    loggedInUser
            );
        }

        LiabilityPayment payment = LiabilityPayment.builder()
                .liability(liability)
                .paymentDate(req.getDate())
                .paymentAmount(total)
                .principalComponent(principal)
                .interestComponent(interest)
                .bank(bank)
                .paymentMethod(req.getPaymentMethod())
                .interestExpense(interestExpense)
                .totalAmount(total)
                .remark(req.getRemark())
                .build();

        liabilityPaymentRepository.save(payment);

        if (bank != null) {
            bankBalanceService.deductAmount(bank, total);
        }

        liability.setOutstandingAmount(
                liability.getOutstandingAmount().subtract(principal)
        );

        if (liability.getOutstandingAmount()
                .compareTo(BigDecimal.ZERO) == 0) {

            liability.setStatus(LiabilityStatus.PAID_OFF);
        }

        Liability saved = liabilityRepository.save(liability);

        return mapToResponse(saved);
    }

    @Transactional
    private LiabilityResponseDto recordSimpleLiabilityPayment(
            Liability liability,
            LiabilityPaymentRequestDto req,
            User loggedInUser) {

        BigDecimal amount = req.getPaymentAmount()
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero.");
        }

        if (amount.compareTo(liability.getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Payment amount exceeds outstanding amount.");
        }

        Bank bank = resolvePaymentBank(req, loggedInUser);

        LiabilityPayment payment = LiabilityPayment.builder()
                .liability(liability)
                .paymentDate(req.getDate())
                .paymentAmount(amount)

                // Not applicable to bills/fees/cards
                .principalComponent(BigDecimal.ZERO)
                .interestComponent(BigDecimal.ZERO)

                .bank(bank)
                .paymentMethod(req.getPaymentMethod())
                .remark(req.getRemark())
                .build();

        liabilityPaymentRepository.save(payment);

        // Actual cash movement
        if (bank != null) {
            bankBalanceService.deductAmount(bank, amount);
        }

        // Reduce outstanding amount
        liability.setOutstandingAmount(
                liability.getOutstandingAmount().subtract(amount)
        );

        if (liability.getOutstandingAmount()
                .compareTo(BigDecimal.ZERO) == 0) {

            liability.setStatus(LiabilityStatus.PAID_OFF);
        }

        Liability saved = liabilityRepository.save(liability);

        return mapToResponse(saved);
    }

//    @Transactional
//    public LiabilityResponseDto recordPayment(Long liabilityId, @Valid LiabilityPaymentRequestDto req, User loggedInUser) {
//
//        Liability liability = findLiability(liabilityId, loggedInUser);
//
//        if (liability.getStatus() != LiabilityStatus.ACTIVE) {
//            throw new IllegalArgumentException(
//                    "Cannot record a payment against a liability with status " + liability.getStatus());
//        }
//
//
//        BigDecimal principalComponent = req.getPrincipalComponent().setScale(2, RoundingMode.HALF_UP);
//        BigDecimal interestComponent = req.getInterestComponent().setScale(2, RoundingMode.HALF_UP);
//
//        if (principalComponent.compareTo(BigDecimal.ZERO) < 0 || interestComponent.compareTo(BigDecimal.ZERO) < 0) {
//            throw new IllegalArgumentException("Payment components cannot be negative.");
//        }
//
//        if (principalComponent.compareTo(BigDecimal.ZERO) == 0 && interestComponent.compareTo(BigDecimal.ZERO) == 0) {
//            throw new IllegalArgumentException("Payment must have a non-zero principal or interest component.");
//        }
//
//        if (principalComponent.compareTo(liability.getOutstandingAmount()) > 0) {
//            throw new IllegalArgumentException(String.format(
//                    "Principal component (₹%s) exceeds outstanding liability amount (₹%s).",
//                    principalComponent.toPlainString(), liability.getOutstandingAmount().toPlainString()));
//        }
//
//        Bank bank = null;
//        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
//            if (req.getBankId() == null) {
//                throw new IllegalArgumentException("Bank is required when payment method is BANK_TRANSFER.");
//            }
//            bank = bankRepository.findByIdAndOwner(req.getBankId(), loggedInUser)
//                    .orElseThrow(() -> new RuntimeException("Bank not found or does not belong to user."));
//        }
//
//        BigDecimal totalAmount = principalComponent.add(interestComponent);
//
//        // ── Create the interest-portion Expense, if any. This is the ONLY bridge
//        //    between Liability and Expense.
//        Expense interestExpense = null;
//        if (interestComponent.compareTo(BigDecimal.ZERO) > 0) {
//            if (req.getInterestCategoryId() == null) {
//                throw new IllegalArgumentException("interestCategoryId is required when interestComponent > 0.");
//            }
//            Category interestCategory = categoryRepository.findByIdAndOwner(req.getInterestCategoryId(), loggedInUser)
//                    .orElseThrow(() -> new RuntimeException("Category not found or does not belong to user."));
//
//            Contact lenderAsContact = liability.getLender();
//            if (lenderAsContact == null) {
//                throw new IllegalArgumentException(
//                        "Liability has no lender contact set; a contact is required to record the interest expense.");
//            }
//
//            interestExpense = Expense.builder()
//                    .owner(loggedInUser)
//                    .category(interestCategory)
//                    .contact(lenderAsContact)
//                    .bank(bank)
//                    .type(TransactionType.EXPENSE)
//                    .sourceType(ExpenseSourceType.LIABILITY_PAYMENT)
//                    .date(req.getDate().atStartOfDay())
//                    .particular("Interest - " + liability.getName())
//                    .amount(interestComponent)
//                    .gstPercentage(BigDecimal.ZERO)
//                    .tdsPercentage(BigDecimal.ZERO)
//                    .total(interestComponent)
//                    .paymentType(PaymentType.ONE_TIME)
//                    .paymentMethod(req.getPaymentMethod())
//                    .paymentStatus(PaymentStatus.COMPLETE)
//                    .remark(req.getRemark())
//                    .build();
//            interestExpense = expenseRepository.save(interestExpense);
//        }
//
//        LiabilityPayment payment = LiabilityPayment.builder()
//                .liability(liability)
//                .paymentDate(req.getDate())
//                .principalComponent(principalComponent)
//                .interestComponent(interestComponent)
//                .totalAmount(totalAmount)
//                .bank(bank)
//                .paymentMethod(req.getPaymentMethod())
//                .interestExpense(interestExpense)
//                .remark(req.getRemark())
//                .build();
//        liabilityPaymentRepository.save(payment);
//
//        // ── Apply the single, real cash movement: full totalAmount debited once.
//        if (bank != null) {
//            bankBalanceService.deductAmount(bank, totalAmount);
//        }
//
//        // ── Reduce outstanding by principal component only. Interest never touches this.
//        liability.setOutstandingAmount(liability.getOutstandingAmount().subtract(principalComponent));
//
//        if (liability.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
//            liability.setOutstandingAmount(BigDecimal.ZERO);
//            liability.setStatus(LiabilityStatus.PAID_OFF);
//        }
//
//        Liability saved = liabilityRepository.save(liability);
//
//        return mapToResponse(saved);
//        return switch (liability.getLiabilityGroup()) {
//
//            case LOAN ->
//                    recordLoanPayment(liability, req, loggedInUser);
//
//            case BILL, FEE, CREDIT_CARD, OTHER ->
//                    recordSimpleLiabilityPayment(liability, req, loggedInUser);
//        };
//    }

    @Transactional
    public LiabilityResponseDto recordPayment(
            Long liabilityId,
            @Valid LiabilityPaymentRequestDto req,
            User loggedInUser) {

        Liability liability = findLiability(liabilityId, loggedInUser);

        if (liability.getStatus() != LiabilityStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Cannot record a payment against a liability with status "
                            + liability.getStatus());
        }

        return switch (liability.getLiabilityGroup()) {

            case LOAN ->
                    recordLoanPayment(liability, req, loggedInUser);

            case BILL, FEE, CREDIT_CARD, OTHER ->
                    recordSimpleLiabilityPayment(liability, req, loggedInUser);
        };
    }


    // ═════════════════════════════════════════════════════════════════════
    // UPDATE — only safe fields
    // ═════════════════════════════════════════════════════════════════════
    private void validateLiabilityTypeAndGroup(LiabilityRequestDto req) {

        if (req.getType() == null || req.getLiabilityGroup() == null) {
            return;
        }

        LiabilityGroup expectedGroup = req.getType().getGroup();

        if (expectedGroup != req.getLiabilityGroup()) {
            throw new IllegalArgumentException(
                    "Liability type " + req.getType()
                            + " does not belong to group "
                            + req.getLiabilityGroup());
        }
    }


    @Transactional
    public LiabilityResponseDto updateLiability(Long id, @Valid LiabilityRequestDto req, User loggedInUser) {

        Liability liability = findLiability(id, loggedInUser);

        boolean hasPayments = !liabilityPaymentRepository
                .findByLiabilityOrderByPaymentDateAsc(liability).isEmpty();
        if (hasPayments) {

            if (!req.getLiabilityGroup().equals(liability.getLiabilityGroup())
                    || !req.getType().equals(liability.getType())) {

                throw new IllegalArgumentException(
                        "Cannot change liability group or type because payments already exist.");
            }
        }

        // Always-safe fields
        liability.setName(req.getName());
        liability.setDescription(req.getDescription());
        liability.setDueDate(req.getDueDate());
        validateLiabilityTypeAndGroup(req);

        liability.setLiabilityGroup(req.getLiabilityGroup());
        liability.setType(req.getType());


        if (req.getInterestRate() != null) {
            liability.setInterestRate(req.getInterestRate());
        }

        if (req.getLenderContactId() != null) {
            Contact lender = contactRepository.findByIdAndOwner(req.getLenderContactId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException("Contact not found or does not belong to user."));
            liability.setLender(lender);
        }

        // Dangerous fields — block once any payment exists
        BigDecimal newPrincipal = req.getOriginalAmount().setScale(2, RoundingMode.HALF_UP);
        if (hasPayments) {

            if (newPrincipal.compareTo(liability.getOriginalAmount()) != 0) {
                throw new IllegalArgumentException(
                        "Cannot change originalAmount because payments already exist against this liability.");
            }
            if (!req.getStartDate().equals(liability.getStartDate())) {
                throw new IllegalArgumentException(
                        "Cannot change startDate because payments already exist against this liability.");
            }
        } else {
            liability.setOriginalAmount(newPrincipal);
            liability.setOutstandingAmount(newPrincipal);
            liability.setStartDate(req.getStartDate());
        }

        Liability updated = liabilityRepository.save(liability);
        return mapToResponse(updated);
    }

    // ═════════════════════════════════════════════════════════════════════
    // DELETE / CANCEL
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public String deleteLiability(Long id, User loggedInUser) {

        Liability liability = findLiability(id, loggedInUser);

        boolean hasPayments = !liabilityPaymentRepository
                .findByLiabilityOrderByPaymentDateAsc(liability).isEmpty();

        if (hasPayments) {
            throw new IllegalArgumentException(
                    "Cannot delete a liability with existing payments. Cancel it instead (PUT /liabilities/{id}/cancel).");
        }

        // Reverse the loan-deposit bank credit, if any, since nothing else references it.
//        if (liability.getBank() != null && liability.getDepositAmount() !=null) {
//            bankBalanceService.deductAmount(liability.getBank(), liability.getDepositAmount());
//        }
        if (liability.getBank() != null
                && liability.getDepositAmount() != null
                && liability.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {

            bankBalanceService.deductAmount(
                    liability.getBank(),
                    liability.getDepositAmount());
        }

        liabilityRepository.delete(liability);
        return "Liability deleted successfully.";
    }

    @Transactional
    public LiabilityResponseDto cancelLiability(Long id, User loggedInUser) {
        Liability liability = findLiability(id, loggedInUser);

        if (liability.getStatus() == LiabilityStatus.PAID_OFF) {
            throw new IllegalArgumentException("Cannot cancel a liability that is already paid off.");
        }

        liability.setStatus(LiabilityStatus.CANCELLED);
        return mapToResponse(liabilityRepository.save(liability));
    }

    // ═════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private Liability findLiability(Long id, User loggedInUser) {
        return liabilityRepository.findByIdAndOwner(id, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Liability not found"));
    }

    private LiabilityResponseDto mapToResponse(Liability liability) {

        BigDecimal totalPrincipalPaid = liabilityPaymentRepository.getTotalPrincipalPaid(liability);
        BigDecimal totalInterestPaid = liabilityPaymentRepository.getTotalInterestPaid(liability);

        List<LiabilityPaymentResponseDto> payments =
                liabilityPaymentRepository.findByLiabilityOrderByPaymentDateAsc(liability)
                        .stream()
                        .map(p -> LiabilityPaymentResponseDto.builder()
                                .id(p.getId())
                                .paymentDate(p.getPaymentDate())
                                .principalComponent(p.getPrincipalComponent())
                                .interestComponent(p.getInterestComponent())
                                .totalAmount(p.getTotalAmount())
                                .paymentMethod(p.getPaymentMethod())
                                .bankId(p.getBank() != null ? p.getBank().getId() : null)
                                .interestExpenseId(p.getInterestExpense() != null ? p.getInterestExpense().getId() : null)
                                .remark(p.getRemark())
                                .build())
                        .collect(Collectors.toList());

        return LiabilityResponseDto.builder()
                .id(liability.getId())
                .name(liability.getName())
                .liabilityGroup(liability.getLiabilityGroup())
                .type(liability.getType())
                .lenderContactId(liability.getLender() != null ? liability.getLender().getId() : null)
                .lenderName(liability.getLender() != null ? liability.getLender().getName() : null)
                .description(liability.getDescription())
                .originalAmount(liability.getOriginalAmount())
                .outstandingAmount(liability.getOutstandingAmount())
                .totalPrincipalPaid(totalPrincipalPaid == null ? BigDecimal.ZERO : totalPrincipalPaid)
                .totalInterestPaid(totalInterestPaid == null ? BigDecimal.ZERO : totalInterestPaid)
                .interestRate(liability.getInterestRate())
                .startDate(liability.getStartDate())
                .dueDate(liability.getDueDate())
                .bankId(liability.getBank() != null ? liability.getBank().getId() : null)
                .bankName(liability.getBank() != null ? liability.getBank().getName() : null)
                .status(liability.getStatus())
                .payments(payments)
                .createdAt(liability.getCreatedAt())
                .updatedAt(liability.getUpdatedAt())
                .build();
    }
}