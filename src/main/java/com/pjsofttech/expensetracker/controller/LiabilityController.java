package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.service.LiabilityService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/liabilities")
@SecurityRequirement(name = "bearerAuth")

public class LiabilityController {

    @Autowired
    private LiabilityService liabilityService;

    @PostMapping
    public ResponseEntity<?> addLiability(@Valid @RequestBody LiabilityRequestDto req,
                                       @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Liability Added",liabilityService.addLiability(req, loggedInUser)));
    }

    @GetMapping
    public ResponseEntity<?> getAllLiabilities(@AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Fetched Liabilities",liabilityService.getAllLiabilities(loggedInUser)));

    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getLiabilityById(@PathVariable Long id,
                                                 @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Fetched Liability",liabilityService.getLiabilityById(id, loggedInUser)));

    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LiabilityResponseDto>> updateLiability(@PathVariable Long id,
                                                                             @Valid @RequestBody LiabilityRequestDto req,
                                                                             @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Liability Updated",liabilityService.updateLiability(id, req, loggedInUser)));

    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<LiabilityResponseDto>> recordPayment(@PathVariable Long id,
                                                                           @Valid @RequestBody LiabilityPaymentRequestDto req,
                                                                           @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Payment Recorded",liabilityService.recordPayment(id, req, loggedInUser)));

    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<LiabilityResponseDto>> cancelLiability(@PathVariable Long id,
                                                                             @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Liability Cancelled",liabilityService.cancelLiability(id, loggedInUser)));

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteLiability(@PathVariable Long id,
                                                               @AuthenticationPrincipal User loggedInUser) {
        return ResponseEntity.ok(ApiResponse.success("Liability Deleted",liabilityService.deleteLiability(id, loggedInUser)));

    }
}