package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AssetCategoryResponseDto {
    private Long id;
    private String name;
}
