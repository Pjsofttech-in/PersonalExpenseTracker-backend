package com.pjsofttech.expensetracker.dto;

import com.pjsofttech.expensetracker.model.AssetGroup;
import com.pjsofttech.expensetracker.model.AssetType;
import com.pjsofttech.expensetracker.model.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetResponseDto {

    private Long id;
    private String name;
    private AssetGroup assetGroup;
    private AssetType type;
    private Long contactId;
    private Long assetCategoryId;
    private String description;

    private BigDecimal purchaseValue;
    private BigDecimal currentValue;
    private LocalDate purchaseDate;

    // Acquisition details (replaces old bankId/expenseId pair)
    private PaymentMethod paymentMethod;
    private Long bankId;
    private String bankName;
    private Long liabilityId;
    private String liabilityName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}