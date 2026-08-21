package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.PaymentMethod;
import com.pjsofttech.expensetracker.model.PaymentStatus;
import com.pjsofttech.expensetracker.model.PaymentType;
import com.pjsofttech.expensetracker.model.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponseDto {
    private Long id;
    private Integer index;

    private LocalDateTime date;
    private TransactionType type;
    private String particular;
    private String remark;

    private ContactResponseDto contact;
    private CategoryResponseDto category;
    private Long bankId;

    private BigDecimal amount;
    private BigDecimal gstPercentage;
    private BigDecimal gstNumber;         // kept as BigDecimal? No — String
    private String gstNumberStr;
    private BigDecimal gstAmount;
    private BigDecimal tdsPercentage;
    private BigDecimal tdsAmount;
    private BigDecimal total;

    /** Sum of all payments across all installments (or total for ONE_TIME). */
    private BigDecimal paid;

    /** total - paid */
    private BigDecimal pending;

    private PaymentType paymentType;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    private Integer numberOfInstallments;

    /** Populated only for INSTALLMENT expenses. */
    private List<InstallmentResponseDto> installments;
}
