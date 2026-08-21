//package com.pjsofttech.expensetracker.controller;
//
//import com.pjsofttech.expensetracker.dto.ExpenseResponseDto;
//import com.pjsofttech.expensetracker.dto.InstallmentPaymentRequestDto;
//import com.pjsofttech.expensetracker.dto.InstallmentRequestDto;
//import com.pjsofttech.expensetracker.service.ExpenseService;
//import jakarta.validation.Valid;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//public class InstallmentController {
//
//    @Autowired
//    private ExpenseService expenseService;
//    @PostMapping("/{expenseId}/installment")
//    public ResponseEntity<ExpenseResponseDto> addInstallment(
//            @PathVariable Long expenseId,
//            @RequestBody InstallmentPaymentRequestDto request ) {
//        ExpenseResponseDto response =
//                expenseService.addInstallment( expenseId, request );
//        return ResponseEntity.ok(response); }
//}
