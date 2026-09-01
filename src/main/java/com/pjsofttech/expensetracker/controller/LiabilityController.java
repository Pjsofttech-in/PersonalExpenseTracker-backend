package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.service.LiabilityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/liabilities")
public class LiabilityController {

    @Autowired
    private LiabilityService liabilityService;

    @PostMapping
    public LiabilityResponseDto addLiability(@Valid @RequestBody LiabilityRequestDto req,
                                             @AuthenticationPrincipal User loggedInUser) {
        return liabilityService.addLiability(req, loggedInUser);
    }

    @GetMapping
    public List<LiabilityResponseDto> getAllLiabilities(@AuthenticationPrincipal User loggedInUser) {
        return liabilityService.getAllLiabilities(loggedInUser);
    }

    @GetMapping("/{id}")
    public LiabilityResponseDto getLiabilityById(@PathVariable Long id,
                                                 @AuthenticationPrincipal User loggedInUser) {
        return liabilityService.getLiabilityById(id, loggedInUser);
    }

    @PutMapping("/{id}")
    public LiabilityResponseDto updateLiability(@PathVariable Long id,
                                                @Valid @RequestBody LiabilityRequestDto req,
                                                @AuthenticationPrincipal User loggedInUser) {
        return liabilityService.updateLiability(id, req, loggedInUser);
    }

    @PostMapping("/{id}/payments")
    public LiabilityResponseDto recordPayment(@PathVariable Long id,
                                              @Valid @RequestBody LiabilityPaymentRequestDto req,
                                              @AuthenticationPrincipal User loggedInUser) {
        return liabilityService.recordPayment(id, req, loggedInUser);
    }

    @PutMapping("/{id}/cancel")
    public LiabilityResponseDto cancelLiability(@PathVariable Long id,
                                                @AuthenticationPrincipal User loggedInUser) {
        return liabilityService.cancelLiability(id, loggedInUser);
    }

    @DeleteMapping("/{id}")
    public String deleteLiability(@PathVariable Long id,
                                  @AuthenticationPrincipal User loggedInUser) {
        return liabilityService.deleteLiability(id, loggedInUser);
    }
}