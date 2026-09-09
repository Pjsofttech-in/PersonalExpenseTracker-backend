package com.pjsofttech.expensetracker.global_exception;

import com.pjsofttech.expensetracker.custom_exceptions.DuplicateCategoryException;
import com.pjsofttech.expensetracker.custom_exceptions.DuplicateEmailException;
import com.pjsofttech.expensetracker.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        return ResponseEntity.badRequest().body(
                ex.getBindingResult().getAllErrors()
        );
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ResponseEntity<?> handleDuplicateCategory(DuplicateCategoryException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<?> handleDuplicateEmail(DuplicateEmailException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

}
