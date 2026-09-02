package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AssetCategoryRequestDto {
    private String name;
}
