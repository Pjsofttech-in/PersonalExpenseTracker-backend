package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.PaymentStatus;
import com.pjsofttech.expensetracker.model.PaymentType;
import lombok.Data;

@Data
public class PaymentTypeRequestDto {
    private PaymentType paymentType;
}
