package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.custom_exceptions.ResourceNotFoundException;
import com.pjsofttech.expensetracker.dto.AssetCategoryRequestDto;
import com.pjsofttech.expensetracker.dto.AssetCategoryResponseDto;
import com.pjsofttech.expensetracker.model.Asset;
import com.pjsofttech.expensetracker.model.AssetCategory;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.AssetCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetCategoryService {
    @Autowired
    private AssetCategoryRepository assetCategoryRepository;

    public AssetCategoryResponseDto addAssetCategory(AssetCategoryRequestDto assetCategoryRequestDto, User loggedInUser) {
        AssetCategory assetCategory = toAsset(assetCategoryRequestDto, loggedInUser);
        assetCategoryRepository.save(assetCategory);
        return toResponse(assetCategory);
    }

    public List<AssetCategoryResponseDto> getAssetCategories(User loggedInUser) {
        List<AssetCategory> assetCategoryList = assetCategoryRepository.findByOwnerOrderByNameAsc(loggedInUser);
        return assetCategoryList.stream()
                .map(assetCategory ->
                {
                    return AssetCategoryResponseDto.builder()
                            .id(assetCategory.getId())
                            .name(assetCategory.getName())
                            .build();
                }
                ).toList();

    }


    private AssetCategory toAsset(AssetCategoryRequestDto assetCategoryRequestDto, User loggedInUser) {
        AssetCategory assetCategory = AssetCategory.builder()
                .name(assetCategoryRequestDto.getName())
                .owner(loggedInUser)
                .build();
        return assetCategory;
    }

    private AssetCategoryResponseDto toResponse(AssetCategory assetCategory) {
        return AssetCategoryResponseDto.builder()
                .id(assetCategory.getId())
                .name(assetCategory.getName())
                .build();
    }

    public String deleteAssetCategoryById(Long id, User loggedInUser) {
        AssetCategory assetCategory =  assetCategoryRepository.findByIdAndOwner(id,loggedInUser)
                .orElseThrow(()->new ResourceNotFoundException("Asset Category Not Found"));
        assetCategoryRepository.delete(assetCategory);
        return "Asset Category "+assetCategory.getName()+" with ID : "+id+"is deleted";
    }
}
