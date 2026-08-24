package com.pjsofttech.expensetracker.dto;
import com.pjsofttech.expensetracker.model.PaymentMethod;
import com.pjsofttech.expensetracker.model.PaymentType;
import com.pjsofttech.expensetracker.model.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class ExpenseRequestDto {
    @NotNull(message = "Contact ID is required")
    private Long contactId;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

//    @NotNull(message = "Bank ID is required")
    private Long bankId;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @NotNull(message = "Date is required")
    private LocalDateTime date;

    private String particular;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
    @DecimalMin(value = "0", message = "GST percentage cannot be negative")
    @DecimalMax(value = "100", message = "GST percentage cannot exceed 100")
    private BigDecimal gstPercentage;
    private String gstNumber;
    @DecimalMin(value = "0", message = "TDS percentage cannot be negative")
    @DecimalMax(value = "100", message = "TDS percentage cannot exceed 100")
    private BigDecimal tdsPercentage;
    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
    private String remark;
    // Only required when paymentType == INSTALLMENT
    private Integer numberOfInstallments;
    @Valid
    private List<InstallmentScheduleDto> installments;
}

