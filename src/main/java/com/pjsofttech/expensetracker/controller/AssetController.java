package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.AssetRequestDto;
import com.pjsofttech.expensetracker.dto.AssetResponseDto;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    @Autowired
    private AssetService assetService;

    // ═════════════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════════════

    @PostMapping
    public AssetResponseDto addAsset(@Valid @RequestBody AssetRequestDto req,
                                     @AuthenticationPrincipal User loggedInUser) {
        return assetService.addAsset(req, loggedInUser);
    }

    // ═════════════════════════════════════════════════════════════════════
    // READ
    // ═════════════════════════════════════════════════════════════════════

    @GetMapping
    public List<AssetResponseDto> getAllAssets(@AuthenticationPrincipal User loggedInUser) {
        return assetService.getAllAssets(loggedInUser);
    }

    @GetMapping("/{id}")
    public AssetResponseDto getAssetById(@PathVariable Long id,
                                         @AuthenticationPrincipal User loggedInUser) {
        return assetService.getAssetById(id, loggedInUser);
    }



    @PutMapping("/{id}")
    public AssetResponseDto updateAsset(@PathVariable Long id,
                                        @Valid @RequestBody AssetRequestDto req,
                                        @AuthenticationPrincipal User loggedInUser) {
        return assetService.updateAsset(id, req, loggedInUser);
    }



    @PutMapping("/{id}/acquisition")
    public AssetResponseDto updateAcquisition(@PathVariable Long id,
                                              @Valid @RequestBody AssetRequestDto req,
                                              @AuthenticationPrincipal User loggedInUser) {
        return assetService.updateAcquisition(id, req, loggedInUser);
    }



    @PutMapping("/{id}/valuation")
    public AssetResponseDto updateCurrentValue(@PathVariable Long id,
                                               @RequestParam BigDecimal currentValue,
                                               @AuthenticationPrincipal User loggedInUser) {
        return assetService.updateCurrentValue(id, currentValue, loggedInUser);
    }



    @DeleteMapping("/{id}")
    public String deleteAsset(@PathVariable Long id,
                              @AuthenticationPrincipal User loggedInUser) {
        return assetService.deleteAsset(id, loggedInUser);
    }
}