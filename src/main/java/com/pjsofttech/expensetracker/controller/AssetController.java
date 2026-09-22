package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.ApiResponse;
import com.pjsofttech.expensetracker.dto.AssetCategoryRequestDto;
import com.pjsofttech.expensetracker.dto.AssetRequestDto;
import com.pjsofttech.expensetracker.dto.AssetResponseDto;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.UserRepository;
import com.pjsofttech.expensetracker.service.AssetCategoryService;
import com.pjsofttech.expensetracker.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
@Tag(
        name = "Assets",
        description = "APIs for managing user assets"
)
@SecurityRequirement(name = "bearerAuth")
public class AssetController {

    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetCategoryService assetCategoryService;
    @Autowired
    private UserRepository userRepository;

    // ═════════════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════════════

    @PostMapping
    @Operation(
            summary = "Create a new asset",
            description = "Creates a new asset for the currently authenticated user."
    )
    public ResponseEntity<?> addAsset(@Valid @RequestBody AssetRequestDto req,
                                     @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Asset Added",
                assetService.addAsset(req, loggedInUser)));
    }

    // ═════════════════════════════════════════════════════════════════════
    // READ
    // ═════════════════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetResponseDto>>> getAllAssets(@AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Assets Fetched",
                assetService.getAllAssets(loggedInUser)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetResponseDto>> getAssetById(@PathVariable Long id,
                                                                      @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Asset Fetched",
                assetService.getAssetById(id, loggedInUser)));
    }



    @PutMapping("/{id}")
    public ResponseEntity<?> updateAsset(@PathVariable Long id,
                                        @Valid @RequestBody AssetRequestDto req,
                                        @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Asset Updated",
                assetService.updateAsset(id, req, loggedInUser)));

    }



    @PutMapping("/{id}/acquisition")
    public ResponseEntity<?> updateAcquisition(@PathVariable Long id,
                                              @Valid @RequestBody AssetRequestDto req,
                                              @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Updated Asset Acquisition",
                assetService.updateAcquisition(id, req, loggedInUser)));
    }



    @PutMapping("/{id}/valuation")
    public ResponseEntity<?> updateCurrentValue(@PathVariable Long id,
                                               @RequestParam BigDecimal currentValue,
                                               @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Updated Current Value",
                assetService.updateCurrentValue(id, currentValue, loggedInUser)));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteAsset(@PathVariable Long id,
                                                           @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Asset Deleted",
                assetService.deleteAsset(id, loggedInUser)));
    }
    @PostMapping("/assets-category")
    public ResponseEntity<?> addAssetCategory(@RequestBody AssetCategoryRequestDto assetCategoryRequestDto,
                                              Authentication authentication){
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found!"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Added Asset Category",
                        assetCategoryService.addAssetCategory(assetCategoryRequestDto,loggedInUser)));
    }

    @GetMapping("/assets-category")
    public ResponseEntity<?> getAssetCategories(
                                              Authentication authentication){
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found!"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Asset Categories Fetched - ",
                        assetCategoryService.getAssetCategories(loggedInUser)));
    }

    @DeleteMapping("/assets-category/{id}")
    public ResponseEntity<?> deleteAssetCategoryById(@PathVariable Long id, @AuthenticationPrincipal User loggedInUser){
        return ResponseEntity.status(HttpStatus.OK)
                .body(assetCategoryService.deleteAssetCategoryById(id,loggedInUser));
    }



}